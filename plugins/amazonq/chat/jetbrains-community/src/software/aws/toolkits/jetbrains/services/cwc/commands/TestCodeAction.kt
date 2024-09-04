// Copyright 2023 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cwc.commands

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import software.aws.toolkits.jetbrains.core.credentials.AwsBearerTokenConnection
import software.aws.toolkits.jetbrains.core.credentials.ToolkitConnectionManager
import software.aws.toolkits.jetbrains.core.credentials.pinning.QConnection

class TestCodeAction : CustomAction(EditorContextCommand.Test) {
    override fun update(e: AnActionEvent) {
        val project = e.getData(CommonDataKeys.PROJECT) ?: return
        val connection = ToolkitConnectionManager.getInstance(project).activeConnectionForFeature(QConnection.getInstance()) as? AwsBearerTokenConnection
        val hashedStartUrl = hashStartUrl(connection.startUrl, "MD5")
        e.presentation.isVisible = (connection != null && connection.startUrl == "[B@4ccabbaa")
    }

    fun hashStartUrl(str: String, algorithm: String): ByteArray = MessageDigest.getInstance(algorithm).digest(str.toByteArray(UTF_8))
}
