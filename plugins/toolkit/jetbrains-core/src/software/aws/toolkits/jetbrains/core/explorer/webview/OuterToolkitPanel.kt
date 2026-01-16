// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.core.explorer.webview

import com.intellij.openapi.project.Project
import com.intellij.ui.components.panels.Wrapper
import com.intellij.util.ui.components.BorderLayoutPanel
import software.aws.toolkit.core.utils.error
import software.aws.toolkit.core.utils.getLogger
import software.aws.toolkits.jetbrains.core.explorer.AwsToolkitExplorerToolWindow
import software.aws.toolkits.jetbrains.utils.isTookitConnected
import javax.swing.JComponent

class OuterToolkitPanel(val project: Project) : BorderLayoutPanel() {
    private val wrapper = Wrapper()
    init {
        isOpaque = false
        addToCenter(wrapper)
        val component = if (!isTookitConnected(project) || shouldPromptToolkitReauth(project)) {
            ToolkitWebviewPanel.getInstance(project).component
        } else {
            AwsToolkitExplorerToolWindow.getInstance(project)
        }

        updateToolkitPanel(component)
    }

    fun updateToolkitPanel(content: JComponent) {
        try {
            wrapper.setContent(content)
        } catch (e: Exception) {
            getLogger<OuterToolkitPanel>().error { "Error while creating window" }
        }
    }
}
