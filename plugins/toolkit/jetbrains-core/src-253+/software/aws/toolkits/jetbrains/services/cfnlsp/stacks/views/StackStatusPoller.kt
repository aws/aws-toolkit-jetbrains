// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cfnlsp.stacks.views

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import software.aws.toolkits.core.utils.getLogger
import software.aws.toolkits.jetbrains.services.cfnlsp.CfnClientService
import software.aws.toolkits.jetbrains.services.cfnlsp.protocol.DescribeStackParams
import java.util.Timer
import java.util.TimerTask

internal class StackStatusPoller(
    project: Project,
    private val stackName: String,
    private val stackArn: String, // Primary identifier
    private val coordinator: StackViewCoordinator,
) : Disposable {

    private val cfnClientService = CfnClientService.getInstance(project)
    private var pollingTimer: Timer? = null
    private var isViewVisible: Boolean = false

    fun setViewVisible(visible: Boolean) {
        isViewVisible = visible
        if (visible) {
            start()
        } else {
            stop()
        }
    }

    fun start() {
        stop() // Stop any existing polling
        pollingTimer = Timer().apply {
            scheduleAtFixedRate(
                object : TimerTask() {
                    override fun run() {
                        if (isViewVisible) {
                            fetchStackData()
                        }
                    }
                },
                0, POLLING_INTERVAL_MS
            ) // Poll every 5 seconds
        }
    }

    fun stop() {
        pollingTimer?.cancel()
        pollingTimer = null
    }

    private fun fetchStackData() {
        cfnClientService.describeStack(DescribeStackParams(stackName))
            .whenComplete { result, error ->
                // Ensure coordinator updates happen on EDT
                ApplicationManager.getApplication().invokeLater {
                    if (error != null) {
                        LOG.warn("Error fetching stack data for $stackName: ${error.message}")
                    } else if (result?.stack == null) {
                        LOG.warn("No stack data received for $stackName")
                    } else {
                        val stack = result.stack
                        coordinator.updateStackStatus(stackArn, stack.stackStatus)

                        // Stop polling if stack reaches terminal state
                        if (!StackStatusUtils.isInTransientState(stack.stackStatus)) {
                            LOG.info("Stack $stackName reached terminal state: ${stack.stackStatus}, stopping polling")
                            stop()
                        }
                    }
                }
            }
    }

    override fun dispose() {
        stop()
    }

    companion object {
        private val LOG = getLogger<StackStatusPoller>()
        private const val POLLING_INTERVAL_MS = 5000L
    }
}
