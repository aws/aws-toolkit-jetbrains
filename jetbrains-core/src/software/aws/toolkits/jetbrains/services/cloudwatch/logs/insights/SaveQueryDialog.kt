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
import software.aws.toolkits.jetbrains.utils.ApplicationThreadPoolScope

class SaveQueryDialog(
    private val project: Project,
    private val query: String,
    private val logGroups: List<String>,
    private val client: CloudWatchLogsClient
) : DialogWrapper(project), CoroutineScope by ApplicationThreadPoolScope("SavingQuery") {
    constructor(project: Project, queryText: String, logGroup: List<String>) :
        this(project = project, query = queryText, logGroups = logGroup, client = project.awsClient())
    private val view = EnterQueryName(project)
    private val action: OkAction = SaveQueryOkAction()
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

    private inner class SaveQueryOkAction : OkAction() {
        init {
            putValue(Action.NAME, message("cloudwatch.logs.save_query"))
        }
        override fun doAction(e: ActionEvent?) {
            super.doAction(e)
            if (doValidateAll().isNotEmpty()) return
            saveQuery()
        }
    }

    fun saveQuery() = launch {
        val request = PutQueryDefinitionRequest.builder().logGroupNames(logGroups).name(view.queryName.text).queryString(query).build()
        val response = client.putQueryDefinition(request)
    }

    fun validateQueryName(view: EnterQueryName): ValidationInfo? {
        if (view.queryName.text.isEmpty()) {
            return ValidationInfo(message("cloudwatch.logs.query_name"), view.queryName)
        }
        return null
    }
}
