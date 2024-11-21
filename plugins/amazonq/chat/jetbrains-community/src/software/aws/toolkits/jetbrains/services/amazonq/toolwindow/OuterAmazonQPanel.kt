// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonq.toolwindow

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.ui.components.panels.Wrapper
import com.intellij.util.ui.components.BorderLayoutPanel
import software.aws.toolkits.core.utils.error
import software.aws.toolkits.core.utils.getLogger
import javax.swing.JComponent

@Service(Service.Level.PROJECT)
class OuterAmazonQPanel : BorderLayoutPanel() {
    private val wrapper = Wrapper()
    init {
        isOpaque = false
        addToCenter(wrapper)
    }

    fun updateQPanel(content: JComponent) {
        try {
            wrapper.setContent(content)
        } catch (e: Exception) {
            getLogger<OuterAmazonQPanel>().error { "Error while creating window" }
        }
    }

    companion object {
        fun getInstance(project: Project): OuterAmazonQPanel = project.service()
    }
}
