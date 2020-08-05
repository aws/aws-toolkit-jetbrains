// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cloudwatch.logs.insights

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import kotlinx.coroutines.CoroutineScope
import software.amazon.awssdk.services.cloudwatchlogs.CloudWatchLogsClient
import software.amazon.awssdk.services.cloudwatchlogs.model.PutQueryDefinitionRequest
import software.aws.toolkits.jetbrains.core.awsClient
import software.aws.toolkits.resources.message
import java.awt.event.ActionEvent
import javax.swing.Action
import javax.swing.JComponent
import kotlinx.coroutines.launch
import software.amazon.awssdk.services.cloudwatchlogs.model.DescribeQueryDefinitionsRequest
import software.aws.toolkits.jetbrains.utils.ApplicationThreadPoolScope
import javax.swing.JOptionPane

class SaveQueryDialog(
    private val project: Project,
    private val query: String,
    private val logGroups: List<String>,
    private val client: CloudWatchLogsClient
) : DialogWrapper(project), CoroutineScope by ApplicationThreadPoolScope("SavingQuery") {
    constructor(project: Project, queryText: String, logGroup: List<String>) :
        this(project = project, query = queryText, logGroups = logGroup, client = project.awsClient())
    private val view = EnterQueryName(project)
    private val action: OkAction = object : OkAction() {
        init {
            putValue(Action.NAME, message("cloudwatch.logs.save_query"))
        }
        override fun doAction(e: ActionEvent?) {
            super.doAction(e)
            if (doValidateAll().isNotEmpty()) return
            saveQuery()
            close(OK_EXIT_CODE)
        }
    }
    init {
        super.init()
        title = "Enter Query Name"
    }

    override fun createCenterPanel(): JComponent? = view.saveQueryPanel
    override fun doValidate(): ValidationInfo? = validateQueryName(view)
    override fun getOKAction(): Action = action
    override fun doCancelAction() {
        super.doCancelAction()
    }
    fun checkQueryName(queryName: String): Boolean {
        val request = DescribeQueryDefinitionsRequest.builder().queryDefinitionNamePrefix(queryName).build()
        val response = client.describeQueryDefinitions(request)
        if (response.queryDefinitions().isEmpty()) {
            return true
        }
        return false
    }
    fun saveQuery() = launch {
        if (checkQueryName(view.queryName.text)) {
            val request = PutQueryDefinitionRequest.builder().logGroupNames(logGroups).name(view.queryName.text).queryString(query).build()
            val response = client.putQueryDefinition(request)
            JOptionPane.showInternalMessageDialog(
                view.saveQueryPanel,
                message("cloudwatch.logs.query_saved_successfully"),
                message("cloudwatch.logs.saved_query_status"),
                JOptionPane.INFORMATION_MESSAGE)
        } else {
            JOptionPane.showInternalMessageDialog(
                view.saveQueryPanel,
                message("cloudwatch.logs.query_not_saved"),
                message("cloudwatch.logs.saved_query_status"),
                JOptionPane.ERROR_MESSAGE)
        }
    }

    fun validateQueryName(view: EnterQueryName): ValidationInfo? {
        if (view.queryName.text.isEmpty()) {
            return ValidationInfo(message("cloudwatch.logs.query_name"), view.queryName)
        }
        return null
    }
}
