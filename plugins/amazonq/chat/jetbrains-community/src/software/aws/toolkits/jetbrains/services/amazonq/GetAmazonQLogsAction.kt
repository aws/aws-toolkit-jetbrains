// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonq

import com.intellij.icons.AllIcons
import com.intellij.ide.actions.RevealFileAction
import com.intellij.ide.logsUploader.LogPacker
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.IconLoader
import com.intellij.ui.ColorUtil
import com.intellij.ui.JBColor
import com.intellij.util.IconUtil
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.runBlocking
import software.amazon.q.jetbrains.utils.notifyInfo
import software.amazon.q.jetbrains.utils.runUnderProgressIfNeeded
import software.aws.toolkits.resources.AmazonQBundle.message
import software.amazon.q.resources.AwsCoreBundle

class GetAmazonQLogsAction : DumbAwareAction(message("amazonq.getLogs.tooltip.text")) {
    private val baseIcon = IconLoader.getIcon("/icons/file.svg", GetAmazonQLogsAction::class.java)

    private val lightIcon by lazy {
        IconUtil.colorize(baseIcon, ColorUtil.brighter(UIUtil.getLabelForeground(), 2))
    }

    override fun update(e: AnActionEvent) {
        e.presentation.icon = if (!JBColor.isBright()) {
            baseIcon
        } else {
            lightIcon
        }
    }

    override fun getActionUpdateThread() = ActionUpdateThread.BGT
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        showLogCollectionWarningGetLogs(project)
    }

    companion object {
        fun showLogCollectionWarningGetLogs(project: Project) {
            if (Messages.showOkCancelDialog(
                    message("amazonq.logs.warning"),
                    message("amazonq.getLogs"),
                    AwsCoreBundle.message("general.ok"),
                    AwsCoreBundle.message("general.cancel"),
                    AllIcons.General.Warning
                ) == 0
            ) {
                runUnderProgressIfNeeded(project, message("amazonq.getLogs"), cancelable = true) {
                    runBlocking {
                        try {
                            RevealFileAction.openFile(LogPacker.packLogs(project))
                        } catch (_: Exception) {
                            notifyInfo(message("amazonq.getLogs"), message("amazonq.logs.error"), project)
                        }
                    }
                }
            }
        }
    }
}
