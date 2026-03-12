// Copyright 2026 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cfnlsp.stacks

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.WindowManager
import software.aws.toolkits.jetbrains.services.cfnlsp.protocol.StackActionPhase
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

internal enum class OperationType { VALIDATION, DEPLOYMENT }

internal data class OperationInfo(
    val stackName: String,
    val type: OperationType,
    val changeSetName: String? = null,
    val startTime: Instant = Instant.now(),
    @Volatile var phase: StackActionPhase = StackActionPhase.VALIDATION_IN_PROGRESS,
    @Volatile var released: Boolean = false,
)

internal interface StatusBarHandle {
    fun update(phase: StackActionPhase)
    fun release()
}

@Service(Service.Level.PROJECT)
internal class CfnOperationStatusService(private val project: Project) : Disposable {

    private val operations = ConcurrentHashMap<Int, OperationInfo>()
    private val nextId = AtomicInteger(0)
    private val refCount = AtomicInteger(0)
    private val scheduler = Executors.newSingleThreadScheduledExecutor()

    @Volatile private var disposeTask: ScheduledFuture<*>? = null

    fun acquire(stackName: String, type: OperationType, changeSetName: String? = null): StatusBarHandle {
        synchronized(this) {
            disposeTask?.cancel(false)
            disposeTask = null
        }

        val id = nextId.getAndIncrement()
        val initialPhase = if (type == OperationType.VALIDATION) {
            StackActionPhase.VALIDATION_IN_PROGRESS
        } else {
            StackActionPhase.DEPLOYMENT_IN_PROGRESS
        }
        operations[id] = OperationInfo(stackName, type, changeSetName, phase = initialPhase)
        refCount.incrementAndGet()
        updateWidget()

        return object : StatusBarHandle {
            private var isReleased = false

            override fun update(phase: StackActionPhase) {
                if (isReleased) return
                operations[id]?.phase = phase
                updateWidget()
            }

            override fun release() {
                if (isReleased) return
                isReleased = true
                operations[id]?.released = true
                val remaining = refCount.decrementAndGet()
                updateWidget()
                if (remaining == 0) {
                    synchronized(this@CfnOperationStatusService) {
                        disposeTask = scheduler.schedule({
                            operations.clear()
                            updateWidget()
                        }, DISPOSE_DELAY_MS, TimeUnit.MILLISECONDS)
                    }
                }
            }
        }
    }

    fun getActiveOperations(): List<OperationInfo> =
        operations.values.filter { !it.released }.sortedByDescending { it.startTime }

    fun getAllOperations(): List<OperationInfo> =
        operations.values.sortedByDescending { it.startTime }

    fun getStatusText(): String {
        val unreleased = operations.values.filter { !it.released }
        if (unreleased.isEmpty()) return ""

        if (unreleased.size == 1) {
            val op = unreleased.first()
            return when {
                !op.phase.isTerminal() -> "${op.type.verb()} ${op.stackName}"
                op.phase.isFailure() -> "${op.type.failedLabel()}: ${op.stackName}"
                else -> "${op.type.doneLabel()} ${op.stackName}"
            }
        }

        val total = unreleased.size
        return "$LABEL ($total)"
    }

    private fun updateWidget() {
        val statusBar = WindowManager.getInstance().getStatusBar(project) ?: return
        statusBar.updateWidget(CfnStatusBarWidgetFactory.ID)
    }

    override fun dispose() {
        scheduler.shutdownNow()
        operations.clear()
    }

    companion object {
        fun getInstance(project: Project): CfnOperationStatusService = project.service()

        private const val DISPOSE_DELAY_MS = 5000L
        private const val LABEL = CfnStatusBarWidgetFactory.DISPLAY_NAME

        private fun OperationType.verb() = when (this) {
            OperationType.VALIDATION -> "Validating"
            OperationType.DEPLOYMENT -> "Deploying"
        }

        private fun OperationType.doneLabel() = when (this) {
            OperationType.VALIDATION -> "Validated"
            OperationType.DEPLOYMENT -> "Deployed"
        }

        private fun OperationType.failedLabel() = when (this) {
            OperationType.VALIDATION -> "Validation Failed"
            OperationType.DEPLOYMENT -> "Deployment Failed"
        }

        internal fun StackActionPhase.isTerminal() = this in setOf(
            StackActionPhase.VALIDATION_COMPLETE,
            StackActionPhase.VALIDATION_FAILED,
            StackActionPhase.DEPLOYMENT_COMPLETE,
            StackActionPhase.DEPLOYMENT_FAILED,
            StackActionPhase.DELETION_COMPLETE,
            StackActionPhase.DELETION_FAILED,
        )

        internal fun StackActionPhase.isFailure() = this in setOf(
            StackActionPhase.VALIDATION_FAILED,
            StackActionPhase.DEPLOYMENT_FAILED,
            StackActionPhase.DELETION_FAILED,
        )
    }
}
