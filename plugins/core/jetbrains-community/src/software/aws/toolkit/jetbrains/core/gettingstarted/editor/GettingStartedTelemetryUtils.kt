// Copyright 2023 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkit.jetbrains.core.gettingstarted.editor

import com.intellij.configurationStore.getPersistentStateComponentStorageLocation
import com.intellij.openapi.project.Project
import software.aws.toolkit.jetbrains.core.credentials.profiles.ProfileCredentialsIdentifierSso
import software.aws.toolkit.jetbrains.core.credentials.sono.IDENTITY_CENTER_ROLE_ACCESS_SCOPE
import software.aws.toolkit.jetbrains.settings.AwsSettings
import software.aws.toolkit.core.utils.exists
import software.aws.toolkit.core.utils.tryOrNull
import software.aws.toolkit.jetbrains.core.credentials.CredentialManager
import software.aws.toolkit.jetbrains.core.credentials.ToolkitAuthManager
import software.aws.toolkits.telemetry.AuthStatus
import software.aws.toolkits.telemetry.StartUpState

fun getConnectionCount(): Long {
    val bearerTokenCount = ToolkitAuthManager.getInstance().listConnections().size
    val iamCredentialCount = CredentialManager.getInstance().getCredentialIdentifiers().count { it !is ProfileCredentialsIdentifierSso }
    return (bearerTokenCount + iamCredentialCount).toLong()
}

fun getEnabledConnectionsForTelemetry(project: Project?): Set<AuthFormId> {
    project ?: return emptySet()
    val enabledConnections = mutableSetOf<Any>()

    addConnectionInfoToSet(
        checkIamConnectionValidity(project),
        enabledConnections,
        AuthFormId.IDENTITYCENTER_EXPLORER,
        AuthFormId.IAMCREDENTIALS_EXPLORER
    )

    addConnectionInfoToSet(
        checkBearerConnectionValidity(project, BearerTokenFeatureSet.CODECATALYST),
        enabledConnections,
        AuthFormId.IDENTITYCENTER_CODECATALYST,
        AuthFormId.BUILDERID_CODECATALYST
    )

    addConnectionInfoToSet(
        checkBearerConnectionValidity(project, BearerTokenFeatureSet.Q),
        enabledConnections,
        AuthFormId.IDENTITYCENTER_Q,
        AuthFormId.BUILDERID_Q
    )
    return enabledConnections.mapTo(mutableSetOf()) { it as AuthFormId }
}

fun getEnabledConnections(project: Project?): String =
    getEnabledConnectionsForTelemetry(project).joinToString(",")

fun getAuthScopesForTelemetry(project: Project?): Set<String> {
    project ?: return emptySet()
    val scopes = mutableSetOf<Any>()

    val explorerConnection = checkIamProfileByCredentialType(project)
    if (explorerConnection !is ActiveConnection.NotConnected && explorerConnection.connectionType == ActiveConnectionType.IAM_IDC) {
        scopes.add(IDENTITY_CENTER_ROLE_ACCESS_SCOPE)
    }

    addConnectionInfoToSet(
        checkBearerConnectionValidity(project, BearerTokenFeatureSet.CODECATALYST),
        dataSet = scopes
    )

    addConnectionInfoToSet(
        checkBearerConnectionValidity(project, BearerTokenFeatureSet.Q),
        dataSet = scopes
    )

    return scopes.mapTo(mutableSetOf()) { it as String }
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

fun addConnectionInfoToSet(
    activeConnection: ActiveConnection,
    dataSet: MutableSet<Any>,
    idcConnection: AuthFormId? = null,
    defaultConnection: AuthFormId? = null,
) {
    if (activeConnection is ActiveConnection.NotConnected) {
        return
    }

    // add enabled connections
    when (activeConnection.connectionType) {
        ActiveConnectionType.IAM_IDC -> {
            idcConnection ?.let {
                dataSet.add(idcConnection)
                return
            }
        } else -> {
            defaultConnection?.let {
                dataSet.add(defaultConnection)
                return
            }
        }
    }

    // add scopes
    val connectionScopes = activeConnection.activeConnectionBearer?.scopes
    if (!connectionScopes.isNullOrEmpty()) {
        dataSet.addAll(connectionScopes)
    }
}

enum class AuthFormId {
    IAMCREDENTIALS_EXPLORER,
    IDENTITYCENTER_EXPLORER,
    BUILDERID_CODECATALYST,
    IDENTITYCENTER_CODECATALYST,
    BUILDERID_Q,
    IDENTITYCENTER_Q,
    UNKNOWN,
}
