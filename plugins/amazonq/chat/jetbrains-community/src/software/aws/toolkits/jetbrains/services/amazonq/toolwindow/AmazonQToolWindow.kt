// Copyright 2023 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonq.toolwindow

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ToolWindowManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking
import software.aws.toolkits.jetbrains.services.amazonq.onboarding.OnboardingPageInteraction
import software.aws.toolkits.jetbrains.services.amazonq.onboarding.OnboardingPageInteractionType
import software.aws.toolkits.jetbrains.services.amazonqCodeScan.runCodeScanMessage
import software.aws.toolkits.jetbrains.services.cwc.controller.TestCommandMessage

@Service(Service.Level.PROJECT)
class AmazonQToolWindow private constructor(
    private val project: Project,
    private val scope: CoroutineScope,
) : Disposable {
    private var chatPanel = AmazonQPanel(project, scope)

    val component
        get() = chatPanel.component

    fun disposeAndRecreate() {
        Disposer.dispose(chatPanel)
        chatPanel = AmazonQPanel(project, scope)
    }

    companion object {
        fun getInstance(project: Project): AmazonQToolWindow = project.service<AmazonQToolWindow>()

        private fun showChatWindow(project: Project) = runInEdt {
            val toolWindow = ToolWindowManager.getInstance(project).getToolWindow(AmazonQToolWindowFactory.WINDOW_ID)
            toolWindow?.show()
        }

        fun getStarted(project: Project) {
            // Make sure the window is shown
            showChatWindow(project)

            // Send the interaction message
            val window = getInstance(project)
            window.chatPanel.sendMessage(OnboardingPageInteraction(OnboardingPageInteractionType.CwcButtonClick), "cwc")
        }

        fun openScanTab(project: Project) {
            showChatWindow(project)
            val window = getInstance(project)
            window.chatPanel.sendMessageAppToUi(runCodeScanMessage, tabType = "codescan")
        }

        fun sendTestMessage(project: Project) {
            runBlocking {
                val a = getInstance(project).chatPanel.getDefaultAppInitContext()
                val b = a.messagesFromAppToUi.publish(TestCommandMessage())
            }

        }
    }

    override fun dispose() {
        // Nothing to do
    }
}
