// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.core.explorer.webview

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.ui.components.panels.Wrapper
import com.intellij.util.ui.components.BorderLayoutPanel
import software.aws.toolkits.core.utils.getLogger
import javax.swing.JComponent

@Service(Service.Level.PROJECT)
class OuterToolkitPanel : BorderLayoutPanel() {
    private val wrapper = Wrapper()
    init {
        isOpaque = false
        addToCenter(wrapper)
    }

    fun updateToolkitPanel(content: JComponent) {
        try {
            wrapper.setContent(content)
        } catch (e: Exception) {
            getLogger<OuterToolkitPanel>().error("Error while creating window")
        }
    }

    companion object {
        fun getInstance(project: Project): OuterToolkitPanel = project.service()
    }
}
