// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonq.toolwindow

import com.intellij.openapi.project.Project
import com.intellij.ui.components.panels.Wrapper
import com.intellij.util.ui.components.BorderLayoutPanel
import software.aws.toolkits.core.utils.error
import software.aws.toolkits.core.utils.getLogger
import software.aws.toolkits.jetbrains.core.webview.BrowserState
import software.aws.toolkits.jetbrains.services.amazonq.QWebviewPanel
import software.aws.toolkits.jetbrains.utils.isQConnected
import software.aws.toolkits.jetbrains.utils.isQExpired
import software.aws.toolkits.telemetry.FeatureId
import javax.swing.JComponent

class OuterAmazonQPanel(val project: Project) : BorderLayoutPanel() {
    private val wrapper = Wrapper()
    init {
        isOpaque = false
        addToCenter(wrapper)
        val component = if (isQConnected(project) && !isQExpired(project)) {
            AmazonQToolWindow.getInstance(project).component
        } else {
            QWebviewPanel.getInstance(project).browser?.prepareBrowser(BrowserState(FeatureId.AmazonQ))
            QWebviewPanel.getInstance(project).component
        }
        updateQPanel(component)
    }

    fun updateQPanel(content: JComponent) {
        try {
            wrapper.setContent(content)
        } catch (e: Exception) {
            getLogger<OuterAmazonQPanel>().error { "Error while creating window" }
        }
    }
}
