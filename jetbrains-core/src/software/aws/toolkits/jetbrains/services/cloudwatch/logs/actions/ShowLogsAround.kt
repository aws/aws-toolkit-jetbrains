// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cloudwatch.logs.actions

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.LangDataKeys
import com.intellij.openapi.project.DumbAware
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.ListTableModel
import software.amazon.awssdk.services.cloudwatchlogs.model.OutputLogEvent
import software.aws.toolkits.jetbrains.services.cloudwatch.logs.CloudWatchLogWindow

class ShowLogsAroundGroup(
    private val logGroup: String,
    private val logStream: String,
    private val treeTable: JBTable
) : ActionGroup("show logs around <localize this>", null, AllIcons.Ide.Link), DumbAware {
    init {
        isPopup = true
    }

    override fun getChildren(e: AnActionEvent?): Array<AnAction> = arrayOf(
        ShowLogsAround(logGroup, logStream, treeTable, "one minute <Localize>", 60 * 1000),
        ShowLogsAround(logGroup, logStream, treeTable, "five minutes <Localize>", 5 * 60 * 1000),
        ShowLogsAround(logGroup, logStream, treeTable, "ten minutes <Localize>", 10 * 60 * 1000)
    )
}

private class ShowLogsAround(
    private val logGroup: String,
    private val logStream: String,
    private val treeTable: JBTable,
    time: String,
    private val timescale: Long
) :
    AnAction("Show logs around $time <LOCALIZE THIS>", "abc <localize this>", AllIcons.Ide.Link), DumbAware {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.getRequiredData(LangDataKeys.PROJECT)
        val window = CloudWatchLogWindow.getInstance(project)
        val selectedObject = (treeTable.model as? ListTableModel<OutputLogEvent>)?.getItem(treeTable.selectedRow) ?: return
        // 1 minute for now
        window.showLogStream(logGroup, logStream, true, selectedObject.timestamp(), timescale)
    }
}
