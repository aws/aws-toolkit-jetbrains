// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.sqs

import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBTabbedPane
import com.intellij.util.ui.EmptyIcon
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import software.aws.toolkits.core.utils.error
import software.aws.toolkits.core.utils.getLogger
import software.aws.toolkits.jetbrains.core.credentials.activeRegion
import software.aws.toolkits.jetbrains.core.toolwindow.ToolkitToolWindow
import software.aws.toolkits.jetbrains.core.toolwindow.ToolkitToolWindowManager
import software.aws.toolkits.jetbrains.core.toolwindow.ToolkitToolWindowType
import software.aws.toolkits.jetbrains.utils.ApplicationThreadPoolScope
import software.aws.toolkits.jetbrains.utils.getCoroutineUiContext
import software.aws.toolkits.resources.message
import javax.swing.BoxLayout
import javax.swing.JPanel

class SqsWindow(private val project: Project) : CoroutineScope by ApplicationThreadPoolScope("openQueue")  {
    private val toolWindow = ToolkitToolWindowManager.getInstance(project, SQS_TOOL_WINDOW)
    private val edtContext = getCoroutineUiContext()

    fun openQueue(queueUrl: String) {
        showQueue(queueUrl, SqsWindowUI(project))
    }

    fun sendMessage(queueUrl: String) {
        val component = SqsWindowUI(project)
        component.mainPanel.selectedIndex = 1
        showQueue(queueUrl, component)
    }

    private fun showQueue(queueUrl: String, component: SqsWindowUI) = launch {
        val queue = Queue(queueUrl, project.activeRegion())

        try {
            val existingWindow = toolWindow.find(queueUrl)
            if (existingWindow != null) {
                withContext(edtContext) {
                    //Need to switch tab if needed
                    existingWindow.show()
                }
                return@launch
            }

            withContext(edtContext) {
                toolWindow.addTab(queue.queueName, component.mainPanel, true, queueUrl)
            }
        } catch (e: Exception) {
            LOG.error(e) { "Exception thrown while trying to open queue '${queue.queueName}'"}
            throw e
        }
    }

    companion object {
        internal val SQS_TOOL_WINDOW = ToolkitToolWindowType(
            "AWS.Sqs",
            message("sqs.toolwindow"),
            EmptyIcon.ICON_0 //TODO: Get and change icons
        )

        fun getInstance(project: Project) = ServiceManager.getService(project, SqsWindow::class.java)
        private val LOG = getLogger<SqsWindow>()
    }
}

// Will add more parameters once window is populated
class SqsWindowUI(private val project: Project) {
    val mainPanel = JBTabbedPane().apply {
        this.add(message("sqs.toolwindow.tab_labels.message.sampling"), JPanel())
        this.add(message("sqs.toolwindow.tab_labels.send.message"), JPanel())
    }
}
