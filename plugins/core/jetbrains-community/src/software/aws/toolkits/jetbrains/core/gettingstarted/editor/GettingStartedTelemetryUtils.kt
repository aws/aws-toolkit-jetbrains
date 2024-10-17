// Copyright 2023 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.core.gettingstarted.editor

import com.intellij.configurationStore.getPersistentStateComponentStorageLocation
import com.intellij.openapi.project.Project
import software.aws.toolkits.core.utils.exists
import software.aws.toolkits.core.utils.tryOrNull
import software.aws.toolkits.jetbrains.core.credentials.CredentialManager
import software.aws.toolkits.jetbrains.core.credentials.ToolkitAuthManager
import software.aws.toolkits.jetbrains.core.credentials.profiles.ProfileCredentialsIdentifierSso
import software.aws.toolkits.jetbrains.settings.AwsSettings
import software.aws.toolkits.telemetry.AuthStatus
import software.aws.toolkits.telemetry.StartUpState

fun getConnectionCount(): Long {
    val bearerTokenCount = ToolkitAuthManager.getInstance().listConnections().size
    val iamCredentialCount = CredentialManager.getInstance().getCredentialIdentifiers().count { it !is ProfileCredentialsIdentifierSso }
    return (bearerTokenCount + iamCredentialCount).toLong()
}

fun getEnabledConnectionsForTelemetry(project: Project?): Set<AuthFormId> {
    project ?: return emptySet()
    val enabledConnections = mutableSetOf<AuthFormId>()

    val explorerConnection = checkIamConnectionValidity(project)
    if (explorerConnection !is ActiveConnection.NotConnected) {
        if (explorerConnection.connectionType == ActiveConnectionType.IAM_IDC) {
            enabledConnections.add(AuthFormId.IDENTITYCENTER_EXPLORER)
        } else {
            enabledConnections.add(
                AuthFormId.IAMCREDENTIALS_EXPLORER
            )
        }
    }
    val codeCatalystConnection = checkBearerConnectionValidity(project, BearerTokenFeatureSet.CODECATALYST)
    if (codeCatalystConnection !is ActiveConnection.NotConnected) {
        if (codeCatalystConnection.connectionType == ActiveConnectionType.IAM_IDC) {
            enabledConnections.add(AuthFormId.IDENTITYCENTER_CODECATALYST)
        } else {
            enabledConnections.add(AuthFormId.BUILDERID_CODECATALYST)
        }
    }

    val codeWhispererConnection = checkBearerConnectionValidity(project, BearerTokenFeatureSet.CODEWHISPERER)
    if (codeWhispererConnection !is ActiveConnection.NotConnected) {
        if (codeWhispererConnection.connectionType == ActiveConnectionType.IAM_IDC) {
            enabledConnections.add(AuthFormId.IDENTITYCENTER_CODEWHISPERER)
        } else {
            enabledConnections.add(
                AuthFormId.BUILDERID_CODEWHISPERER
            )
        }
    }

    val qConnection = checkBearerConnectionValidity(project, BearerTokenFeatureSet.Q)
    if (qConnection !is ActiveConnection.NotConnected) {
        if (qConnection.connectionType == ActiveConnectionType.IAM_IDC) {
            enabledConnections.add(AuthFormId.IDENTITYCENTER_Q)
        } else {
            enabledConnections.add(
                AuthFormId.BUILDERID_Q
            )
        }
    }
    return enabledConnections
}

fun getEnabledConnections(project: Project?): String =
    getEnabledConnectionsForTelemetry(project).joinToString(",")

fun getAuthScopesForTelemetry(project: Project?): Set<String> {
    project ?: return emptySet()
    val scopes = mutableSetOf<String>()

    fun addScopes(connection: ActiveConnection) {
        if (connection !is ActiveConnection.NotConnected) {
            val connectionScopes = connection.activeConnectionBearer?.scopes
            if (connectionScopes != null) {
                scopes.addAll(connectionScopes)
            }
        }
    }

    val explorerConnection = checkIamConnectionValidity(project)
    addScopes(explorerConnection)

    val codeCatalystConnection = checkBearerConnectionValidity(project, BearerTokenFeatureSet.CODECATALYST)
    addScopes(codeCatalystConnection)

    val codeWhispererConnection = checkBearerConnectionValidity(project, BearerTokenFeatureSet.CODEWHISPERER)
    addScopes(codeWhispererConnection)

    val qConnection = checkBearerConnectionValidity(project, BearerTokenFeatureSet.Q)
    addScopes(qConnection)

    return scopes
}

fun getAuthScopes(project: Project?): String =
    getAuthScopesForTelemetry(project).joinToString(",")

fun getStartupState(): StartUpState {
    val hasStartedToolkitBefore = tryOrNull {
        getPersistentStateComponentStorageLocation(AwsSettings::class.java)?.exists()
    } ?: true
    return if (hasStartedToolkitBefore) StartUpState.Reloaded else StartUpState.FirstStartUp
}

fun getAuthStatus(project: Project) = when (checkConnectionValidity(project)) {
    is ActiveConnection.ExpiredIam,
    is ActiveConnection.ExpiredBearer,
    -> AuthStatus.Expired
    is ActiveConnection.ValidIam,
    is ActiveConnection.ValidBearer,
    -> AuthStatus.Connected
    else -> AuthStatus.NotConnected
}

enum class AuthFormId {
    IAMCREDENTIALS_EXPLORER,
    IDENTITYCENTER_EXPLORER,
    BUILDERID_CODECATALYST,
    IDENTITYCENTER_CODECATALYST,
    BUILDERID_CODEWHISPERER,
    IDENTITYCENTER_CODEWHISPERER,
    BUILDERID_Q,
    IDENTITYCENTER_Q,
    UNKNOWN,
}
