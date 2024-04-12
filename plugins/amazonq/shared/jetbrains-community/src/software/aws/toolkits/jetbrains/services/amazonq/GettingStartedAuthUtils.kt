// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonq

import com.intellij.openapi.project.Project
import software.aws.toolkits.core.utils.tryOrNull
import software.aws.toolkits.jetbrains.core.credentials.LegacyManagedBearerSsoConnection
import software.aws.toolkits.jetbrains.core.credentials.Login
import software.aws.toolkits.jetbrains.core.credentials.ToolkitConnectionManager
import software.aws.toolkits.jetbrains.core.credentials.loginSso
import software.aws.toolkits.jetbrains.core.credentials.pinning.CodeWhispererConnection
import software.aws.toolkits.jetbrains.core.credentials.sono.Q_SCOPES
import software.aws.toolkits.jetbrains.core.gettingstarted.SourceOfEntry
import software.aws.toolkits.jetbrains.core.gettingstarted.editor.getConnectionCount
import software.aws.toolkits.jetbrains.core.gettingstarted.editor.getEnabledConnections
import software.aws.toolkits.jetbrains.core.gettingstarted.editor.getSourceOfEntry
import software.aws.toolkits.telemetry.AuthTelemetry
import software.aws.toolkits.telemetry.FeatureId
import software.aws.toolkits.telemetry.Result



fun requestCredentialsForQ(
    project: Project,
    login: Login,
    initialConnectionCount: Int = getConnectionCount(),
    initialAuthConnections: String = getEnabledConnections(
        project
    ),
    isFirstInstance: Boolean = false,
    connectionInitiatedFromExplorer: Boolean = false,
    connectionInitiatedFromQChatPanel: Boolean = false
): Boolean {
    // try to scope upgrade if we have a codewhisperer connection
    val codeWhispererConnection = ToolkitConnectionManager.getInstance(project).activeConnectionForFeature(CodeWhispererConnection.getInstance())
    if (codeWhispererConnection is LegacyManagedBearerSsoConnection) {
        codeWhispererConnection.let {
            return tryOrNull {
                loginSso(project, it.startUrl, it.region, Q_SCOPES)
            } != null
        }
    }

    val isAuthenticationSuccessful = when (login) {
        is Login.BuilderId -> {
            login.loginBuilderId(project)
        }

        is Login.IdC -> {
            login.loginIdc(project)
        }

        else -> {
            false
        }
    }

    if (isAuthenticationSuccessful) {
        AuthTelemetry.addConnection(
            project,
            source = getSourceOfEntry(SourceOfEntry.Q, isFirstInstance, connectionInitiatedFromExplorer, connectionInitiatedFromQChatPanel),
            featureId = FeatureId.Q,
            credentialSourceId = login.id,
            isAggregated = true,
            attempts = 0, // TODO: fix it
            result = Result.Succeeded
        )
        AuthTelemetry.addedConnections(
            project,
            source = getSourceOfEntry(SourceOfEntry.Q, isFirstInstance, connectionInitiatedFromExplorer, connectionInitiatedFromQChatPanel),
            authConnectionsCount = initialConnectionCount,
            newAuthConnectionsCount = getConnectionCount() - initialConnectionCount,
            enabledAuthConnections = initialAuthConnections,
            newEnabledAuthConnections = getEnabledConnections(project),
            attempts = 0, // TODO: fix it
            result = Result.Succeeded
        )
    } else {
        AuthTelemetry.addConnection(
            project,
            source = getSourceOfEntry(SourceOfEntry.Q, isFirstInstance, connectionInitiatedFromExplorer, connectionInitiatedFromQChatPanel),
            featureId = FeatureId.Q,
            credentialSourceId = login.id,
            isAggregated = false,
            attempts = 0, // TODO: fix it
            result = Result.Cancelled,
        )
    }
    return isAuthenticationSuccessful
}
