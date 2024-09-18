// Copyright 2023 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cwc.commands

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import software.aws.toolkits.jetbrains.core.credentials.AwsBearerTokenConnection
import software.aws.toolkits.jetbrains.core.credentials.ToolkitConnectionManager
import software.aws.toolkits.jetbrains.core.credentials.pinning.QConnection

class GenerateUnitTestAction : CustomAction(EditorContextCommand.GenerateUnitTest) {
    override fun update(e: AnActionEvent) {
        val project = e.getData(CommonDataKeys.PROJECT) ?: return
        val connection = ToolkitConnectionManager.getInstance(project).activeConnectionForFeature(QConnection.getInstance()) as? AwsBearerTokenConnection
        e.presentation.isEnabledAndVisible = connection?.startUrl == "https://amzn.awsapps.com/start"
    }
}
