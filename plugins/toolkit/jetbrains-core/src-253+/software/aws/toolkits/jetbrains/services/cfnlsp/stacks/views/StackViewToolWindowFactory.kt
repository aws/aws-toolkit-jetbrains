// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cfnlsp.stacks.views

import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import software.aws.toolkits.resources.message

internal class StackViewToolWindowFactory : ToolWindowFactory {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        runInEdt {
            toolWindow.installWatcher(toolWindow.contentManager)
        }
        // Don't create any initial content - tabs will be created when stacks are opened
    }

    override fun init(toolWindow: ToolWindow) {
        toolWindow.stripeTitle = message("cloudformation.lsp.stack.view")
    }

    override fun shouldBeAvailable(project: Project): Boolean = false
}
