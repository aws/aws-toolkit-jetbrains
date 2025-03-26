// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonq

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.dsl.builder.panel
import icons.AwsIcons
import software.aws.toolkits.jetbrains.services.amazonq.toolwindow.AMAZON_Q_WINDOW_ID
import software.aws.toolkits.jetbrains.services.amazonq.toolwindow.AmazonQToolWindow
import software.aws.toolkits.jetbrains.services.codewhisperer.util.CodeWhispererConstants.runScanKey
import software.aws.toolkits.jetbrains.utils.isQWebviewsAvailable
import software.aws.toolkits.jetbrains.utils.isRunningOnRemoteBackend
import software.aws.toolkits.resources.AmazonQBundle
import software.aws.toolkits.resources.message
import software.aws.toolkits.telemetry.UiTelemetry
import javax.swing.JComponent

class QOpenPanelAction : AnAction(message("action.q.openchat.text"), null, AwsIcons.Logos.AWS_Q) {
    override fun actionPerformed(e: AnActionEvent) {
        if (isRunningOnRemoteBackend()) return
        val project = e.getRequiredData(CommonDataKeys.PROJECT)
        if (!isQWebviewsAvailable()) {
            QWebviewNotAvailable(project).show()
            return
        }
        UiTelemetry.click(project, "q_openChat")
        ToolWindowManager.getInstance(project).getToolWindow(AMAZON_Q_WINDOW_ID)?.activate(null, true)
        if (e.getData(runScanKey) == true) {
            AmazonQToolWindow.openScanTab(project)
        }
    }
}

class QWebviewNotAvailable(project: Project) : DialogWrapper(project) {
    override fun createCenterPanel(): JComponent = panel {
        row {
            icon(Messages.getWarningIcon())
            label(AmazonQBundle.message("amazonqChat.incompatible.text", ApplicationNamesInfo.getInstance().fullProductName)).bold()
        }
        row {
            label(AmazonQBundle.message("amazonQChat.incomptible.text.fix"))
        }.visible(ApplicationInfo.getInstance().build.productCode == "AI")
    }

    init {
        title = AmazonQBundle.message("amazonQChat.incompatible.title")
        init()
    }
}
