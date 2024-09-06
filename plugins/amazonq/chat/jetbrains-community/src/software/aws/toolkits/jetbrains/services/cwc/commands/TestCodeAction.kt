// Copyright 2023 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cwc.commands

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import software.aws.toolkits.jetbrains.core.credentials.AwsBearerTokenConnection
import software.aws.toolkits.jetbrains.core.credentials.ToolkitConnectionManager
import software.aws.toolkits.jetbrains.core.credentials.pinning.QConnection
import java.security.MessageDigest
import kotlin.text.Charsets.UTF_8

class TestCodeAction : CustomAction(EditorContextCommand.Test) {
    override fun update(e: AnActionEvent) {
        val project = e.getData(CommonDataKeys.PROJECT) ?: return
        val connection = ToolkitConnectionManager.getInstance(project).activeConnectionForFeature(QConnection.getInstance()) as? AwsBearerTokenConnection
        if (connection == null) {
            // Hide the action by default if no connection found.
            e.presentation.isEnabledAndVisible = false
            return
        }

        e.presentation.isEnabledAndVisible = hashStartUrl(connection.startUrl) == "222dc4c61f7b8d08cdaf133a0a29f994"
    }

    private fun ByteArray.toHex() = joinToString(separator = "") { byte -> "%02x".format(byte) }

    private fun hashStartUrl(startUrl: String, algorithm: String = "MD5"): String = MessageDigest.getInstance(algorithm).digest(startUrl.toByteArray(UTF_8)).toHex()
}
