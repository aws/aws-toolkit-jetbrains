// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonq

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.SystemInfo
import software.amazon.awssdk.services.codewhispererruntime.model.IdeCategory
import software.amazon.awssdk.services.codewhispererruntime.model.OperatingSystem
import software.amazon.awssdk.services.codewhispererruntime.model.UserContext
import software.aws.toolkits.jetbrains.core.credentials.ToolkitConnection
import software.aws.toolkits.jetbrains.core.credentials.ToolkitConnectionManager
import software.aws.toolkits.jetbrains.core.credentials.pinning.CodeWhispererConnection
import software.aws.toolkits.jetbrains.core.credentials.sono.isSono
import software.aws.toolkits.jetbrains.services.telemetry.ClientMetadata

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

fun codeWhispererUserContext(): UserContext = ClientMetadata.getDefault().let {
    val osForCodeWhisperer: OperatingSystem =
        when {
            SystemInfo.isWindows -> OperatingSystem.WINDOWS
            SystemInfo.isMac -> OperatingSystem.MAC
            // For now, categorize everything else as "Linux" (Linux/FreeBSD/Solaris/etc)
            else -> OperatingSystem.LINUX
        }

    UserContext.builder()
        .ideCategory(IdeCategory.JETBRAINS)
        .operatingSystem(osForCodeWhisperer)
        .product(FEATURE_EVALUATION_PRODUCT_NAME)
        .clientId(it.clientId)
        .ideVersion(it.awsVersion)
        .build()
}
