// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cloudwatch.logs.insights.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAware
import software.aws.toolkits.jetbrains.core.awsClient
import software.aws.toolkits.jetbrains.core.explorer.actions.SingleResourceNodeAction
import software.aws.toolkits.jetbrains.services.cloudwatch.logs.CloudWatchLogsNode
import software.aws.toolkits.jetbrains.services.cloudwatch.logs.insights.QueryEditorDialog
import software.aws.toolkits.jetbrains.services.cloudwatch.logs.insights.RetrieveSavedQueries
import software.aws.toolkits.jetbrains.services.cloudwatch.logs.insights.sampleQueries
import software.aws.toolkits.resources.message

class QueryGroupAction : SingleResourceNodeAction<CloudWatchLogsNode>(message("cloudwatch.logs.query")), DumbAware {
    /*
    * RetrieveSavedQueries(selected.nodeProject.awsClient()).getSavedQueries()
while(true){
    if (RetrieveSavedQueries.allQueries.size != sampleQueries.size){
        QueryEditorDialog(selected.nodeProject, selected.logGroupName).show()
        break
    }
}*/
    override fun actionPerformed(selected: CloudWatchLogsNode, e: AnActionEvent) {
        RetrieveSavedQueries(selected.nodeProject.awsClient()).getSavedQueries()
        while(true){
            println(RetrieveSavedQueries.allQueries.size)
            if (RetrieveSavedQueries.allQueries.size != sampleQueries.size){
                println("Inside")
                QueryEditorDialog(selected.nodeProject, selected.logGroupName, initialParametersDisplayed = true).show()
                break
            }
        }

    }
}
