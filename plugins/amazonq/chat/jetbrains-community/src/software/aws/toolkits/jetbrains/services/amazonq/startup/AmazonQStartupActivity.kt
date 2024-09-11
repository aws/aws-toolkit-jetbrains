// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonq.startup

import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.waitForSmartMode
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.wm.ToolWindowManager
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.time.withTimeout
import software.aws.toolkits.core.utils.getLogger
import software.aws.toolkits.core.utils.warn
import software.aws.toolkits.jetbrains.core.gettingstarted.emitUserState
import software.aws.toolkits.jetbrains.services.amazonq.toolwindow.AmazonQToolWindow
import software.aws.toolkits.jetbrains.services.amazonq.toolwindow.AmazonQToolWindowFactory
import software.aws.toolkits.jetbrains.services.codewhisperer.explorer.CodeWhispererExplorerActionManager
import software.aws.toolkits.jetbrains.services.codewhisperer.settings.CodeWhispererSettings
import software.aws.toolkits.jetbrains.services.cwc.editor.context.project.ProjectContextController
import java.lang.management.ManagementFactory
import java.time.Duration
import java.util.concurrent.atomic.AtomicBoolean

class AmazonQStartupActivity : ProjectActivity {
    private val runOnce = AtomicBoolean(false)

    override suspend fun execute(project: Project) {
        // initialize html contents in BGT so users don't have to wait when they open the tool window
        AmazonQToolWindow.getInstance(project)

        if (CodeWhispererExplorerActionManager.getInstance().getIsFirstRestartAfterQInstall()) {
            runInEdt {
                val toolWindow = ToolWindowManager.getInstance(project).getToolWindow(AmazonQToolWindowFactory.WINDOW_ID) ?: return@runInEdt
                toolWindow.show()
                CodeWhispererExplorerActionManager.getInstance().setIsFirstRestartAfterQInstall(false)
            }
        }
        startLsp(project)
        if (runOnce.get()) return
        emitUserState(project)
        runOnce.set(true)
    }

    private suspend fun startLsp(project: Project) {
        // Automatically start the project context LSP after some delay when average CPU load is below 30%.
        // The CPU load requirement is to avoid competing with native JetBrains indexing and other CPU expensive OS processes
        // In the future we will decouple LSP start and indexing start to let LSP perform other tasks.
        if (CodeWhispererSettings.getInstance().isProjectContextEnabled()) {
            val startLspIndexingDuration = Duration.ofMinutes(30)
            project.waitForSmartMode()
            try {
                withTimeout(startLspIndexingDuration) {
                    while (true) {
                        val cpuUsage = ManagementFactory.getOperatingSystemMXBean().systemLoadAverage
                        if (cpuUsage > 0 && cpuUsage < 30) {
                            ProjectContextController.getInstance(project = project)
                            break
                        } else {
                            delay(60_000) // Wait for 60 seconds
                        }
                    }
                }
            } catch (e: TimeoutCancellationException) {
                LOG.warn { "Failed to start LSP server due to time out" }
            } catch (e: Exception) {
                LOG.warn { "Failed to start LSP server" }
            }
        }
    }

    companion object {
        private val LOG = getLogger<AmazonQStartupActivity>()
    }
}
