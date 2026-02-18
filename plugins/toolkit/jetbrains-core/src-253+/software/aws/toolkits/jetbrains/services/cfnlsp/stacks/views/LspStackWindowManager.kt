// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cfnlsp.stacks.views

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.RegisterToolWindowTask
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.content.Content
import com.intellij.ui.content.ContentFactory
import icons.AwsIcons

@Service(Service.Level.PROJECT)
class LspStackWindowManager(private val project: Project) {

    private var currentStackUI: LspStackUI? = null
    private var currentContent: Content? = null

    fun openStack(stackName: String, stackId: String) {
        val toolWindowManager = ToolWindowManager.getInstance(project)
        val toolWindow = toolWindowManager.getToolWindow("AWS CloudFormation Stacks")
            ?: toolWindowManager.registerToolWindow(
                RegisterToolWindowTask(
                    id = "AWS CloudFormation Stacks",
                    icon = AwsIcons.Logos.CLOUD_FORMATION_TOOL
                )
            )

        val contentManager = toolWindow.contentManager

        // Proper cleanup of existing resources
        currentStackUI?.dispose()
        currentContent?.let { contentManager.removeContent(it, true) }

        // Create new stack UI
        val newStackUI = LspStackUI(project, stackName, stackId)
        currentStackUI = newStackUI

        // Create single content with tabbed interface
        val content = ContentFactory.getInstance().createContent(
            newStackUI.getComponent(),
            "Stack Details",
            false
        )
        content.isCloseable = false
        currentContent = content

        contentManager.addContent(content)
        contentManager.setSelectedContent(content)

        newStackUI.start()
        toolWindow.show()
    }

    companion object {
        fun getInstance(project: Project): LspStackWindowManager = project.service()
    }
}
