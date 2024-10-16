// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonq

import com.intellij.openapi.project.Project
import software.aws.toolkits.jetbrains.core.credentials.ToolkitConnection
import software.aws.toolkits.jetbrains.core.credentials.ToolkitConnectionManager
import software.aws.toolkits.jetbrains.core.credentials.pinning.CodeWhispererConnection
import software.aws.toolkits.jetbrains.core.credentials.sono.isSono

fun <T> calculateIfIamIdentityCenterConnection(project: Project, calculationTask: (connection: ToolkitConnection) -> T): T? =
    ToolkitConnectionManager.getInstance(project).activeConnectionForFeature(CodeWhispererConnection.getInstance())?.let {
        calculateIfIamIdentityCenterConnection(it, calculationTask)
    }

fun <T> calculateIfIamIdentityCenterConnection(connection: ToolkitConnection, calculationTask: (connection: ToolkitConnection) -> T): T? =
    if (connection.isSono()) {
        null
    } else {
        calculationTask(connection)
    }

fun <T> calculateIfBIDConnection(project: Project, calculationTask: (connection: ToolkitConnection) -> T): T? =
    ToolkitConnectionManager.getInstance(project).activeConnectionForFeature(CodeWhispererConnection.getInstance())?.let {
        if (it.isSono()) {
            calculationTask(it)
        } else {
            null
        }
    }
