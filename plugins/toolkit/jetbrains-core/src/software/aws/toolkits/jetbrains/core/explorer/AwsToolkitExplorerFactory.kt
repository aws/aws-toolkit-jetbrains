// Copyright 2022 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.core.explorer

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.openapi.wm.ex.ToolWindowEx
import software.aws.toolkits.jetbrains.AwsToolkit
import software.aws.toolkits.jetbrains.core.credentials.ToolkitConnection
import software.aws.toolkits.jetbrains.core.credentials.ToolkitConnectionManagerListener
import software.aws.toolkits.jetbrains.core.experiments.ExperimentsActionGroup
import software.aws.toolkits.jetbrains.core.explorer.webview.ToolkitWebviewPanel
import software.aws.toolkits.jetbrains.core.help.HelpIds
import software.aws.toolkits.jetbrains.utils.actions.OpenBrowserAction
import software.aws.toolkits.resources.message

class AwsToolkitExplorerFactory : ToolWindowFactory, DumbAware {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        toolWindow.helpId = HelpIds.EXPLORER_WINDOW.id

        if (toolWindow is ToolWindowEx) {
            val actionManager = ActionManager.getInstance()
            toolWindow.setTitleActions(listOf(actionManager.getAction("aws.toolkit.explorer.titleBar")))
            toolWindow.setAdditionalGearActions(
                DefaultActionGroup().apply {
                    add(
                        OpenBrowserAction(
                            title = message("explorer.view_documentation"),
                            url = AwsToolkit.AWS_DOCS_URL
                        )
                    )
                    add(
                        OpenBrowserAction(
                            title = message("explorer.view_source"),
                            icon = AllIcons.Vcs.Vendors.Github,
                            url = AwsToolkit.GITHUB_URL
                        )
                    )
                    add(
                        OpenBrowserAction(
                            title = message("explorer.create_new_issue"),
                            icon = AllIcons.Vcs.Vendors.Github,
                            url = "${AwsToolkit.GITHUB_URL}/issues/new/choose"
                        )
                    )
                    add(actionManager.getAction("aws.toolkit.showFeedback"))
                    add(ExperimentsActionGroup())
                    add(actionManager.getAction("aws.settings.show"))
                }
            )
        }

        val contentManager = toolWindow.contentManager
        val content = contentManager.factory.createContent(AwsToolkitExplorerToolWindow.getInstance(project), null, false).also {
            it.isCloseable = true
            it.isPinnable = true
        }
        contentManager.addContent(content)
        toolWindow.activate(null)
        contentManager.setSelectedContent(content)

        project.messageBus.connect().subscribe(
            ToolkitConnectionManagerListener.TOPIC,
            object : ToolkitConnectionManagerListener {
                override fun activeConnectionChanged(newConnection: ToolkitConnection?) {
                    // TODO: switch between 1. browser login view / 2. explorer tree
                    val component = if (isToolkitConnected(newConnection)) {
                        println("ExplorerFactory: toolkit is connected, rendering explorer tree...")
                        AwsToolkitExplorerToolWindow.getInstance(project)
                    } else {
                        println("ExplorerFactory: toolkit is NOT connected, rendering browser view...")
                        ToolkitWebviewPanel.getInstance(project).component
                    }

                    val myContent = contentManager.factory.createContent(component, null, false).also {
                        it.isCloseable = true
                        it.isPinnable = true
                    }

                    runInEdt {
                        contentManager.removeAllContents(true)
                        contentManager.addContent(myContent)
                    }
                }
            }
        )
    }

    override fun init(toolWindow: ToolWindow) {
        toolWindow.stripeTitle = message("aws.notification.title")
    }

    companion object {
        const val TOOLWINDOW_ID = "aws.toolkit.explorer"
    }
}

// TODO: not sure how do we define Toolkit "connected"
private fun isToolkitConnected(newConnection: ToolkitConnection?): Boolean {
    return false
}
