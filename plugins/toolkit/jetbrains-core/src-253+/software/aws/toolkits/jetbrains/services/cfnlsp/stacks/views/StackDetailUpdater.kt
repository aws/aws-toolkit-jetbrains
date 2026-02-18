// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cfnlsp.stacks.views

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import software.aws.toolkit.core.utils.getLogger
import software.aws.toolkits.jetbrains.services.cfnlsp.CfnClientService
import software.aws.toolkits.jetbrains.services.cfnlsp.protocol.DescribeStackParams
import java.util.Timer
import java.util.TimerTask

internal class StackDetailUpdater(
    project: Project,
    private val stackName: String,
    private val stackArn: String, // Primary identifier
    private val coordinator: StackViewCoordinator,
) : Disposable {

    private val cfnClientService = CfnClientService.getInstance(project)
    private var pollingTimer: Timer? = null
    private var isViewVisible: Boolean = false

    fun setViewVisible(visible: Boolean) {
        LOG.info("Setting view visibility for $stackName: $visible")
        isViewVisible = visible
        if (visible) {
            start()
        } else {
            stop()
        }
    }

    fun start() {
        LOG.info("Starting polling for stack: $stackName")
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
                0, 5000L
            ) // Poll every 5 seconds
        }
    }

    fun stop() {
        if (pollingTimer != null) {
            LOG.info("Stopping polling for stack: $stackName")
            pollingTimer?.cancel()
            pollingTimer = null
        }
    }

    private fun fetchStackData() {
        LOG.debug("Fetching stack data for: $stackName")
        cfnClientService.describeStack(DescribeStackParams(stackName))
            .whenComplete { result, error ->
                // Ensure coordinator updates happen on EDT
                ApplicationManager.getApplication().invokeLater {
                    if (error != null) {
                        LOG.warn("Error fetching stack data for $stackName: ${error.message}")
                    } else {
                        result?.stack?.let { stack ->
                            LOG.debug("Received stack status for $stackName: ${stack.stackStatus}")
                            coordinator.updateStackStatus(stackArn, stack.stackStatus)

                            // Stop polling if stack reaches terminal state
                            if (!StackStatusUtils.isInTransientState(stack.stackStatus)) {
                                LOG.info("Stack $stackName reached terminal state: ${stack.stackStatus}, stopping polling")
                                stop()
                            }
                        } ?: LOG.warn("No stack data received for $stackName")
                    }
                }
            }
    }

    override fun dispose() {
        LOG.info("Disposing updater for stack: $stackName")
        stop()
    }

    companion object {
        private val LOG = getLogger<StackDetailUpdater>()
    }
}
