// Copyright 2022 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.core.explorer

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ex.ToolWindowEx
import software.aws.toolkits.jetbrains.core.experiments.ExperimentsActionGroup
import software.aws.toolkits.jetbrains.utils.actions.OpenBrowserAction
import software.aws.toolkits.resources.message

class ToolkitToolWindowListener(project: Project) {
    private val toolWindow by lazy { AwsToolkitExplorerToolWindow.toolWindow(project) }
    private val defaultActions by lazy { DefaultActionGroup() }
    private val actionManager by lazy { ActionManager.getInstance() }
    private val explorerActions by lazy { listOf(actionManager.getAction("aws.toolkit.explorer.titleBar")) }
    private val explorerGearActions by lazy {
        DefaultActionGroup().apply {
            add(
                OpenBrowserAction(
                    title = message("explorer.view_documentation"),
                    url = "https://docs.aws.amazon.com/console/toolkit-for-jetbrains"
                )
            )
            add(
                OpenBrowserAction(
                    title = message("explorer.view_source"),
                    icon = AllIcons.Vcs.Vendors.Github,
                    url = "https://github.com/aws/aws-toolkit-jetbrains"
                )
            )
            add(
                OpenBrowserAction(
                    title = message("explorer.create_new_issue"),
                    icon = AllIcons.Vcs.Vendors.Github,
                    url = "https://github.com/aws/aws-toolkit-jetbrains/issues/new/choose"
                )
            )
            add(actionManager.getAction("aws.toolkit.showFeedback"))
            add(ExperimentsActionGroup())
            add(actionManager.getAction("aws.settings.show"))
        }
    }

    private val developerToolsActions by lazy {
        listOf(
            actionManager.getAction("aws.toolkit.showFeedback")
        )
    }

    fun tabChanged(tabName: String) {
        // compiler can't smart cast since property is lazy and therefore has a custom getter
        toolWindow.let {
            if (it is ToolWindowEx) {
                if (tabName == message("explorer.toolwindow.title")) {
                    it.setTitleActions(explorerActions)
                    it.setAdditionalGearActions(explorerGearActions)
                } else if (tabName == message("aws.developer.tools.tab.title")) {
                    it.setTitleActions(developerToolsActions)
                    it.setAdditionalGearActions(defaultActions)
                }
            }
        }
    }
}
