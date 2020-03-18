// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cloudwatch.logs.editor

import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.Separator
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.ui.PopupHandler
import com.intellij.ui.components.JBTextField
import com.intellij.ui.components.panels.Wrapper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import software.aws.toolkits.jetbrains.core.credentials.activeCredentialProvider
import software.aws.toolkits.jetbrains.core.credentials.activeRegion
import software.aws.toolkits.jetbrains.services.cloudwatch.logs.LogStreamActor
import software.aws.toolkits.jetbrains.services.cloudwatch.logs.actions.OpenCurrentInEditor
import software.aws.toolkits.jetbrains.services.cloudwatch.logs.actions.ShowLogsAroundGroup
import software.aws.toolkits.jetbrains.services.cloudwatch.logs.actions.TailLogs
import software.aws.toolkits.jetbrains.services.cloudwatch.logs.actions.WrapLogs
import software.aws.toolkits.jetbrains.utils.ApplicationThreadPoolScope
import software.aws.toolkits.resources.message
import java.time.Duration
import javax.swing.JLabel
import javax.swing.JPanel

class CloudWatchLogStream(
    private val project: Project,
    private val logGroup: String,
    private val logStream: String,
    private val startTime: Long? = null,
    private val duration: Duration? = null
) : CoroutineScope by ApplicationThreadPoolScope("CloudWatchLogStream"), Disposable {
    lateinit var content: JPanel
    lateinit var logsPanel: Wrapper
    lateinit var searchLabel: JLabel
    lateinit var searchField: JBTextField
    lateinit var toolbarHolder: Wrapper

    private val logStreamTable: LogStreamTable = LogStreamTable(project, logGroup, logStream)

    init {
        logsPanel.setContent(logStreamTable.component)
        Disposer.register(this, logStreamTable)
        searchLabel.text = "${project.activeCredentialProvider().displayName} => ${project.activeRegion().displayName} => $logGroup => $logStream"
        searchField.emptyText.text = message("cloudwatch.logs.filter_logs")

        addAction()
        addActionToolbar()

        launch {
            if (startTime != null && duration != null) {
                logStreamTable.channel.send(LogStreamActor.Messages.LOAD_INITIAL_RANGE(startTime, duration))
            } else {
                logStreamTable.channel.send(LogStreamActor.Messages.LOAD_INITIAL())
            }
        }
    }
/*
    searchField.addActionListener(object : ActionListener {
        private var lastText: String? = null
        override fun actionPerformed(e: ActionEvent?) {
            if (searchField.text == lastText) {
                return
            }
            lastText = searchField.text
            val client: CloudWatchLogsAsyncClient = project.awsClient()
            launch {
                val response = client.filterLogEvents {
                    it
                        .logGroupName(logGroup)
                        .logStreamNames(logStream)
                        .filterPattern(lastText)
                }.await()
                val events = response.events()
            }
        }
    })*/

    private fun addAction() {
        val actionGroup = DefaultActionGroup()
        actionGroup.add(OpenCurrentInEditor(project, logStream, logStreamTable.logsTable.listTableModel))
        actionGroup.add(Separator())
        actionGroup.add(ShowLogsAroundGroup(logGroup, logStream, logStreamTable.logsTable))
        PopupHandler.installPopupHandler(
            logStreamTable.logsTable,
            actionGroup,
            ActionPlaces.EDITOR_POPUP,
            ActionManager.getInstance()
        )
    }

    private fun addActionToolbar() {
        val actionGroup = DefaultActionGroup()
        actionGroup.add(OpenCurrentInEditor(project, logStream, logStreamTable.logsTable.listTableModel))
        actionGroup.add(TailLogs(logStreamTable.channel))
        actionGroup.add(WrapLogs(logStreamTable.logsTable))
        val toolbar = ActionManager.getInstance().createActionToolbar("CloudWatchLogStream", actionGroup, false)
        toolbarHolder.setContent(toolbar.component)
    }

    override fun dispose() {}
}
