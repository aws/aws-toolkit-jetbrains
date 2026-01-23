// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkit.jetbrains.core.gettingstarted

import com.intellij.openapi.project.Project
import software.aws.toolkit.core.utils.tryOrNull
import software.aws.toolkit.jetbrains.core.credentials.LegacyManagedBearerSsoConnection
import software.aws.toolkit.jetbrains.core.credentials.ManagedBearerSsoConnection
import software.aws.toolkit.jetbrains.core.credentials.ProfileSsoManagedBearerSsoConnection
import software.aws.toolkit.jetbrains.core.credentials.ReauthSource
import software.aws.toolkit.jetbrains.core.credentials.ToolkitConnectionManager
import software.aws.toolkit.jetbrains.core.credentials.loginSso
import software.aws.toolkit.jetbrains.core.credentials.pinning.QConnection
import software.aws.toolkit.jetbrains.core.credentials.reauthConnectionIfNeeded
import software.aws.toolkit.jetbrains.core.credentials.sono.Q_SCOPES
import software.aws.toolkit.jetbrains.core.gettingstarted.editor.SourceOfEntry
import software.aws.toolkit.jetbrains.core.gettingstarted.editor.getAuthScopes
import software.aws.toolkit.jetbrains.core.gettingstarted.editor.getAuthStatus
import software.aws.toolkit.jetbrains.core.gettingstarted.editor.getConnectionCount
import software.aws.toolkit.jetbrains.core.gettingstarted.editor.getEnabledConnections
import software.aws.toolkit.jetbrains.core.gettingstarted.editor.getSourceOfEntry
import software.aws.toolkit.jetbrains.core.gettingstarted.editor.getStartupState
import software.aws.toolkit.jetbrains.core.region.AwsRegionProvider
import software.aws.toolkit.jetbrains.utils.pluginAwareExecuteOnPooledThread
import software.aws.toolkit.resources.AwsCoreBundle
import software.aws.toolkits.telemetry.AuthTelemetry
import software.aws.toolkits.telemetry.FeatureId
import software.aws.toolkits.telemetry.MetricResult
import software.aws.toolkits.telemetry.Result
import software.aws.toolkits.telemetry.Telemetry

fun requestCredentialsForCodeWhisperer(
    project: Project,
    popupBuilderIdTab: Boolean = true,
    initialConnectionCount: Long = getConnectionCount(),
    initialAuthConnections: String = getEnabledConnections(
        project
    ),
    isFirstInstance: Boolean = false,
    connectionInitiatedFromExplorer: Boolean = false,
    isReauth: Boolean = false,
): Boolean {
    val authenticationDialog = SetupAuthenticationDialog(
        project,
        state = SetupAuthenticationDialogState().also {
            if (popupBuilderIdTab) {
                it.selectedTab.set(SetupAuthenticationTabs.BUILDER_ID)
            }
        },
        tabSettings = mapOf(
            SetupAuthenticationTabs.IDENTITY_CENTER to AuthenticationTabSettings(
                disabled = false,
                notice = SetupAuthenticationNotice(
                    SetupAuthenticationNotice.NoticeType.WARNING,
                    AwsCoreBundle.message("gettingstarted.setup.codewhisperer.use_builder_id"),
                    CODEWHISPERER_AUTH_LEARN_MORE_LINK
                )
            ),
            SetupAuthenticationTabs.BUILDER_ID to AuthenticationTabSettings(
                disabled = false,
                notice = SetupAuthenticationNotice(
                    SetupAuthenticationNotice.NoticeType.WARNING,
                    AwsCoreBundle.message("gettingstarted.setup.codewhisperer.use_identity_center"),
                    CODEWHISPERER_AUTH_LEARN_MORE_LINK
                )
            ),
            SetupAuthenticationTabs.IAM_LONG_LIVED to AuthenticationTabSettings(
                disabled = true,
                notice = SetupAuthenticationNotice(
                    SetupAuthenticationNotice.NoticeType.ERROR,
                    AwsCoreBundle.message("gettingstarted.setup.auth.no_iam"),
                    CODEWHISPERER_AUTH_LEARN_MORE_LINK

                )
            )
        ),
        scopes = Q_SCOPES,
        promptForIdcPermissionSet = false,
        sourceOfEntry = SourceOfEntry.CODEWHISPERER,
        featureId = FeatureId.Codewhisperer,
        isFirstInstance = isFirstInstance,
        connectionInitiatedFromExplorer = connectionInitiatedFromExplorer
    )
    val isAuthenticationSuccessful = authenticationDialog.showAndGet()
    if (isAuthenticationSuccessful) {
        Telemetry.auth.addConnection.use {
            it.source(getSourceOfEntry(SourceOfEntry.CODEWHISPERER, isFirstInstance, connectionInitiatedFromExplorer))
                .featureId(FeatureId.Codewhisperer)
                .credentialSourceId(authenticationDialog.authType)
                .isAggregated(true)
                .attempts(authenticationDialog.attempts + 1)
                .result(MetricResult.Succeeded)
                .isReAuth(isReauth)
        }
        AuthTelemetry.addedConnections(
            project,
            source = getSourceOfEntry(SourceOfEntry.CODEWHISPERER, isFirstInstance, connectionInitiatedFromExplorer),
            authConnectionsCount = initialConnectionCount,
            newAuthConnectionsCount = getConnectionCount() - initialConnectionCount,
            enabledAuthConnections = initialAuthConnections,
            newEnabledAuthConnections = getEnabledConnections(project),
            attempts = authenticationDialog.attempts + 1,
            result = Result.Succeeded
        )
    } else {
        Telemetry.auth.addConnection.use {
            it.source(getSourceOfEntry(SourceOfEntry.CODEWHISPERER, isFirstInstance, connectionInitiatedFromExplorer))
                .featureId(FeatureId.Codewhisperer)
                .credentialSourceId(authenticationDialog.authType)
                .isAggregated(false)
                .attempts(authenticationDialog.attempts + 1)
                .result(MetricResult.Cancelled)
                .isReAuth(isReauth)
        }
    }
    return isAuthenticationSuccessful
}

@Deprecated("pending moving to Q package")
fun requestCredentialsForQ(
    project: Project,
    initialConnectionCount: Long = getConnectionCount(),
    initialAuthConnections: String = getEnabledConnections(
        project
    ),
    isFirstInstance: Boolean = false,
    connectionInitiatedFromExplorer: Boolean = false,
    connectionInitiatedFromQChatPanel: Boolean = false,
    isReauth: Boolean,
): Boolean {
    // try to scope upgrade if we have a codewhisperer connection
    val qConnection = ToolkitConnectionManager.Companion.getInstance(project).activeConnectionForFeature(QConnection.Companion.getInstance())
    if (qConnection is LegacyManagedBearerSsoConnection) {
        qConnection.let {
            return tryOrNull {
                loginSso(project, it.startUrl, it.region, Q_SCOPES)
            } != null
        }
    }

    val dialogState = SetupAuthenticationDialogState().apply {
        (qConnection as? ProfileSsoManagedBearerSsoConnection)?.let { connection ->
            idcTabState.apply {
                profileName = connection.configSessionName
                startUrl = connection.startUrl
                region = AwsRegionProvider.Companion.getInstance().let { it.get(connection.region) ?: it.defaultRegion() }
            }

            // default selected tab is IdC, but just in case
            selectedTab.set(SetupAuthenticationTabs.IDENTITY_CENTER)
        } ?: run {
            selectedTab.set(SetupAuthenticationTabs.BUILDER_ID)
        }
    }

    val authenticationDialog = SetupAuthenticationDialog(
        project,
        state = dialogState,
        tabSettings = mapOf(
            SetupAuthenticationTabs.IDENTITY_CENTER to AuthenticationTabSettings(
                disabled = false,
                notice = SetupAuthenticationNotice(
                    SetupAuthenticationNotice.NoticeType.WARNING,
                    AwsCoreBundle.message("gettingstarted.setup.codewhisperer.use_builder_id"),
                    CODEWHISPERER_AUTH_LEARN_MORE_LINK
                )
            ),
            SetupAuthenticationTabs.BUILDER_ID to AuthenticationTabSettings(
                disabled = false,
                notice = SetupAuthenticationNotice(
                    SetupAuthenticationNotice.NoticeType.WARNING,
                    AwsCoreBundle.message("gettingstarted.setup.codewhisperer.use_identity_center"),
                    CODEWHISPERER_AUTH_LEARN_MORE_LINK
                )
            ),
            SetupAuthenticationTabs.IAM_LONG_LIVED to AuthenticationTabSettings(
                disabled = true,
                notice = SetupAuthenticationNotice(
                    SetupAuthenticationNotice.NoticeType.ERROR,
                    AwsCoreBundle.message("gettingstarted.setup.auth.no_iam"),
                    CODEWHISPERER_AUTH_LEARN_MORE_LINK
                )
            )
        ),
        scopes = Q_SCOPES,
        promptForIdcPermissionSet = false,
        sourceOfEntry = SourceOfEntry.Q,
        featureId = FeatureId.AmazonQ,
        connectionInitiatedFromQChatPanel = connectionInitiatedFromQChatPanel
    )

    val isAuthenticationSuccessful = authenticationDialog.showAndGet()
    if (isAuthenticationSuccessful) {
        Telemetry.auth.addConnection.use {
            it.source(
                getSourceOfEntry(
                    SourceOfEntry.Q,
                    isFirstInstance,
                    connectionInitiatedFromExplorer,
                    connectionInitiatedFromQChatPanel
                )
            )
                .featureId(FeatureId.AmazonQ)
                .credentialSourceId(authenticationDialog.authType)
                .isAggregated(true)
                .attempts(authenticationDialog.attempts + 1)
                .result(MetricResult.Succeeded)
                .isReAuth(isReauth)
        }
        AuthTelemetry.addedConnections(
            project,
            source = getSourceOfEntry(
                SourceOfEntry.Q,
                isFirstInstance,
                connectionInitiatedFromExplorer,
                connectionInitiatedFromQChatPanel
            ),
            authConnectionsCount = initialConnectionCount,
            newAuthConnectionsCount = getConnectionCount() - initialConnectionCount,
            enabledAuthConnections = initialAuthConnections,
            newEnabledAuthConnections = getEnabledConnections(project),
            attempts = authenticationDialog.attempts + 1,
            result = Result.Succeeded
        )
    } else {
        Telemetry.auth.addConnection.use {
            it.source(
                getSourceOfEntry(
                    SourceOfEntry.Q,
                    isFirstInstance,
                    connectionInitiatedFromExplorer,
                    connectionInitiatedFromQChatPanel
                )
            )
                .featureId(FeatureId.AmazonQ)
                .credentialSourceId(authenticationDialog.authType)
                .isAggregated(false)
                .attempts(authenticationDialog.attempts + 1)
                .result(MetricResult.Cancelled)
                .isReAuth(isReauth)
        }
    }
    return isAuthenticationSuccessful
}

fun reauthenticateWithQ(project: Project) {
    val connection = ToolkitConnectionManager.Companion.getInstance(project).activeConnectionForFeature(QConnection.Companion.getInstance())
    if (connection !is ManagedBearerSsoConnection) return
    pluginAwareExecuteOnPooledThread {
        reauthConnectionIfNeeded(project, connection, isReAuth = true, reauthSource = ReauthSource.Q_CHAT)
    }
}

fun emitUserState(project: Project) {
    AuthTelemetry.userState(
        project,
        authEnabledConnections = getEnabledConnections(project),
        authScopes = getAuthScopes(project),
        authStatus = getAuthStatus(project),
        source = getStartupState().toString()
    )
}

const val CODEWHISPERER_AUTH_LEARN_MORE_LINK = "https://docs.aws.amazon.com/codewhisperer/latest/userguide/codewhisperer-auth.html"
