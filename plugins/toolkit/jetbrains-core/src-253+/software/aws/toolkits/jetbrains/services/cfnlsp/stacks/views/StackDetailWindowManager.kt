// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cfnlsp.stacks.views

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.content.ContentFactory

@Service(Service.Level.PROJECT)
class StackDetailWindowManager(private val project: Project) {

    private var currentStackUI: StackDetailView? = null

    fun openStack(stackName: String, stackId: String) {
        val toolWindowManager = ToolWindowManager.getInstance(project)
        val toolWindow = toolWindowManager.getToolWindow("AWS CloudFormation Stack Details")
            ?: return // Tool window must be registered in plugin.xml

        val contentManager = toolWindow.contentManager

        // Proper cleanup of existing resources
        currentStackUI?.dispose()

        // Create new stack UI
        val newStackUI = StackDetailView(project, stackName, stackId)
        currentStackUI = newStackUI

        // Reuse existing content or create if none exists
        val content = if (contentManager.contentCount > 0) {
            contentManager.getContent(0)!!.apply {
                displayName = stackName
            }
        } else {
            ContentFactory.getInstance().createContent(null, stackName, false).also {
                it.isCloseable = false
                contentManager.addContent(it)
            }
        }

        // Update the content with new stack UI
        content.component = newStackUI.getComponent()
        contentManager.setSelectedContent(content)

        newStackUI.start()
        toolWindow.show()
    }

    companion object {
        fun getInstance(project: Project): StackDetailWindowManager = project.service()
    }
}
