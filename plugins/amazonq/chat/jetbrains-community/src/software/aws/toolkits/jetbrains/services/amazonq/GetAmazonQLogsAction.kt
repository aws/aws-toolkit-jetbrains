// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonq

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionUiKind
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.IconLoader
import com.intellij.ui.ColorUtil
import com.intellij.ui.JBColor
import com.intellij.util.IconUtil
import com.intellij.util.ui.UIUtil
import software.aws.toolkits.jetbrains.utils.notifyInfo
import software.aws.toolkits.resources.AmazonQBundle
import software.aws.toolkits.resources.AmazonQBundle.message

class GetAmazonQLogsAction : DumbAwareAction(
    AmazonQBundle.message("amazonq.getLogs.tooltip.text")
) {

    override fun update(e: AnActionEvent) {
        super.update(e)
        val baseIcon = IconLoader.getIcon("/icons/file.svg", GetAmazonQLogsAction::class.java)
        e.presentation.icon = if (!JBColor.isBright()) {
            baseIcon
        } else {
            IconUtil.colorize(baseIcon, ColorUtil.brighter(UIUtil.getLabelForeground(), 2))
        }
    }

    override fun getActionUpdateThread() = ActionUpdateThread.BGT
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        showLogCollectionWarningGetLogs(project)
    }

    companion object {
        fun showLogCollectionWarningGetLogs(project: Project) {
            try {
                val action = ActionManager.getInstance().getAction("CollectZippedLogs")
                val datacontxt = SimpleDataContext.builder().add(CommonDataKeys.PROJECT, project).build()
                val ae = AnActionEvent.createEvent(action, datacontxt, null, ActionPlaces.UNKNOWN, ActionUiKind.POPUP, null)
                action.actionPerformed(ae)
            } catch (_: Exception) {
                notifyInfo(message("amazonq.getLogs.tooltip.text"), message("amazonq.logs.warning"))
            }
        }
    }
}
