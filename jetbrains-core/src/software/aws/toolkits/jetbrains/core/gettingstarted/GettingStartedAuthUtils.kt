// Copyright 2023 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.core.gettingstarted

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import org.slf4j.LoggerFactory
import software.amazon.awssdk.profiles.Profile
import software.amazon.awssdk.services.ssooidc.model.InvalidGrantException
import software.amazon.awssdk.services.ssooidc.model.InvalidRequestException
import software.amazon.awssdk.services.ssooidc.model.SsoOidcException
import software.aws.toolkits.core.credentials.CredentialIdentifier
import software.aws.toolkits.core.utils.error
import software.aws.toolkits.core.utils.tryOrNull
import software.aws.toolkits.jetbrains.core.credentials.AwsBearerTokenConnection
import software.aws.toolkits.jetbrains.core.credentials.BearerSsoConnection
import software.aws.toolkits.jetbrains.core.credentials.ConfigFilesFacade
import software.aws.toolkits.jetbrains.core.credentials.DefaultConfigFilesFacade
import software.aws.toolkits.jetbrains.core.credentials.LegacyManagedBearerSsoConnection
import software.aws.toolkits.jetbrains.core.credentials.ManagedBearerSsoConnection
import software.aws.toolkits.jetbrains.core.credentials.ProfileSsoManagedBearerSsoConnection
import software.aws.toolkits.jetbrains.core.credentials.ToolkitAuthManager
import software.aws.toolkits.jetbrains.core.credentials.ToolkitConnectionManager
import software.aws.toolkits.jetbrains.core.credentials.UserConfigSsoSessionProfile
import software.aws.toolkits.jetbrains.core.credentials.loginSso
import software.aws.toolkits.jetbrains.core.credentials.pinning.CodeWhispererConnection
import software.aws.toolkits.jetbrains.core.credentials.pinning.QConnection
import software.aws.toolkits.jetbrains.core.credentials.profiles.ProfileCredentialsIdentifierSso
import software.aws.toolkits.jetbrains.core.credentials.profiles.SsoSessionConstants
import software.aws.toolkits.jetbrains.core.credentials.reauthConnectionIfNeeded
import software.aws.toolkits.jetbrains.core.credentials.sono.CODEWHISPERER_SCOPES
import software.aws.toolkits.jetbrains.core.credentials.sono.IDENTITY_CENTER_ROLE_ACCESS_SCOPE
import software.aws.toolkits.jetbrains.core.credentials.sono.Q_SCOPES
import software.aws.toolkits.jetbrains.core.gettingstarted.editor.getConnectionCount
import software.aws.toolkits.jetbrains.core.gettingstarted.editor.getEnabledConnections
import software.aws.toolkits.jetbrains.core.gettingstarted.editor.getSourceOfEntry
import software.aws.toolkits.jetbrains.core.region.AwsRegionProvider
import software.aws.toolkits.resources.message
import software.aws.toolkits.telemetry.AuthTelemetry
import software.aws.toolkits.telemetry.FeatureId
import software.aws.toolkits.telemetry.Result
import java.io.IOException

private val LOG = LoggerFactory.getLogger("GettingStartedAuthUtils")

fun rolePopupFromConnection(
    project: Project,
    connection: AwsBearerTokenConnection,
    configFilesFacade: ConfigFilesFacade = DefaultConfigFilesFacade(),
    isFirstInstance: Boolean = false
) {
    runInEdt {
        if (!connection.id.startsWith(SsoSessionConstants.SSO_SESSION_SECTION_NAME) || connection !is BearerSsoConnection) {
            // require reauth if it's not a profile-based sso connection
            requestCredentialsForExplorer(project, isFirstInstance = isFirstInstance, connectionInitiatedFromExplorer = true)
        } else {
            val session = connection.id.substringAfter("${SsoSessionConstants.SSO_SESSION_SECTION_NAME}:")

            val tokenProvider = if (!connection.scopes.contains(IDENTITY_CENTER_ROLE_ACCESS_SCOPE)) {
                val scopes = connection.scopes + IDENTITY_CENTER_ROLE_ACCESS_SCOPE
                val profile = UserConfigSsoSessionProfile(
                    configSessionName = session,
                    ssoRegion = connection.region,
                    startUrl = connection.startUrl,
                    scopes = scopes
                )

                authAndUpdateConfig(project, profile, configFilesFacade) {
                    Messages.showErrorDialog(project, it, message("gettingstarted.explorer.iam.add"))
                } ?: return@runInEdt
            } else {
                reauthConnectionIfNeeded(project, connection)
                connection
            }.getConnectionSettings().tokenProvider

            IdcRolePopup(project, connection.region, session, tokenProvider).show()
        }
    }
}

fun requestCredentialsForCodeWhisperer(
    project: Project,
    popupBuilderIdTab: Boolean = true,
    initialConnectionCount: Int = getConnectionCount(),
    initialAuthConnections: String = getEnabledConnections(
        project
    ),
    isFirstInstance: Boolean = false,
    connectionInitiatedFromExplorer: Boolean = false
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
                    message("gettingstarted.setup.codewhisperer.use_builder_id"),
                    CODEWHISPERER_AUTH_LEARN_MORE_LINK
                )
            ),
            SetupAuthenticationTabs.BUILDER_ID to AuthenticationTabSettings(
                disabled = false,
                notice = SetupAuthenticationNotice(
                    SetupAuthenticationNotice.NoticeType.WARNING,
                    message("gettingstarted.setup.codewhisperer.use_identity_center"),
                    CODEWHISPERER_AUTH_LEARN_MORE_LINK
                )
            ),
            SetupAuthenticationTabs.IAM_LONG_LIVED to AuthenticationTabSettings(
                disabled = true,
                notice = SetupAuthenticationNotice(
                    SetupAuthenticationNotice.NoticeType.ERROR,
                    message("gettingstarted.setup.auth.no_iam"),
                    CODEWHISPERER_AUTH_LEARN_MORE_LINK

                )
            )
        ),
        scopes = CODEWHISPERER_SCOPES + Q_SCOPES,
        promptForIdcPermissionSet = false,
        sourceOfEntry = SourceOfEntry.CODEWHISPERER,
        featureId = FeatureId.Codewhisperer,
        isFirstInstance = isFirstInstance,
        connectionInitiatedFromExplorer = connectionInitiatedFromExplorer
    )
    val isAuthenticationSuccessful = authenticationDialog.showAndGet()
    if (isAuthenticationSuccessful) {
        AuthTelemetry.addConnection(
            project,
            source = getSourceOfEntry(SourceOfEntry.CODEWHISPERER, isFirstInstance, connectionInitiatedFromExplorer),
            featureId = FeatureId.Codewhisperer,
            credentialSourceId = authenticationDialog.authType,
            isAggregated = true,
            attempts = authenticationDialog.attempts + 1,
            result = Result.Succeeded
        )
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
        AuthTelemetry.addConnection(
            project,
            source = getSourceOfEntry(SourceOfEntry.CODEWHISPERER, isFirstInstance, connectionInitiatedFromExplorer),
            featureId = FeatureId.Codewhisperer,
            credentialSourceId = authenticationDialog.authType,
            isAggregated = false,
            attempts = authenticationDialog.attempts + 1,
            result = Result.Cancelled,
        )
    }
    return isAuthenticationSuccessful
}

fun requestCredentialsForQ(
    project: Project,
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

    val dialogState = SetupAuthenticationDialogState().apply {
        (codeWhispererConnection as? ProfileSsoManagedBearerSsoConnection)?.let { connection ->
            idcTabState.apply {
                profileName = connection.configSessionName
                startUrl = connection.startUrl
                region = AwsRegionProvider.getInstance().let { it.get(connection.region) ?: it.defaultRegion() }
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
                    message("gettingstarted.setup.codewhisperer.use_builder_id"),
                    CODEWHISPERER_AUTH_LEARN_MORE_LINK
                )
            ),
            SetupAuthenticationTabs.BUILDER_ID to AuthenticationTabSettings(
                disabled = false,
                notice = SetupAuthenticationNotice(
                    SetupAuthenticationNotice.NoticeType.WARNING,
                    message("gettingstarted.setup.codewhisperer.use_identity_center"),
                    CODEWHISPERER_AUTH_LEARN_MORE_LINK
                )
            ),
            SetupAuthenticationTabs.IAM_LONG_LIVED to AuthenticationTabSettings(
                disabled = true,
                notice = SetupAuthenticationNotice(
                    SetupAuthenticationNotice.NoticeType.ERROR,
                    message("gettingstarted.setup.auth.no_iam"),
                    CODEWHISPERER_AUTH_LEARN_MORE_LINK
                )
            )
        ),
        scopes = CODEWHISPERER_SCOPES + Q_SCOPES,
        promptForIdcPermissionSet = false,
        sourceOfEntry = SourceOfEntry.Q,
        featureId = FeatureId.Q, // TODO: Update Q  in common
        connectionInitiatedFromQChatPanel = connectionInitiatedFromQChatPanel
    )

    val isAuthenticationSuccessful = authenticationDialog.showAndGet()
    if (isAuthenticationSuccessful) {
        AuthTelemetry.addConnection(
            project,
            source = getSourceOfEntry(SourceOfEntry.Q, isFirstInstance, connectionInitiatedFromExplorer, connectionInitiatedFromQChatPanel),
            featureId = FeatureId.Q,
            credentialSourceId = authenticationDialog.authType,
            isAggregated = true,
            attempts = authenticationDialog.attempts + 1,
            result = Result.Succeeded
        )
        AuthTelemetry.addedConnections(
            project,
            source = getSourceOfEntry(SourceOfEntry.Q, isFirstInstance, connectionInitiatedFromExplorer, connectionInitiatedFromQChatPanel),
            authConnectionsCount = initialConnectionCount,
            newAuthConnectionsCount = getConnectionCount() - initialConnectionCount,
            enabledAuthConnections = initialAuthConnections,
            newEnabledAuthConnections = getEnabledConnections(project),
            attempts = authenticationDialog.attempts + 1,
            result = Result.Succeeded
        )
    } else {
        AuthTelemetry.addConnection(
            project,
            source = getSourceOfEntry(SourceOfEntry.Q, isFirstInstance, connectionInitiatedFromExplorer, connectionInitiatedFromQChatPanel),
            featureId = FeatureId.Q,
            credentialSourceId = authenticationDialog.authType,
            isAggregated = false,
            attempts = authenticationDialog.attempts + 1,
            result = Result.Cancelled,
        )
    }
    return isAuthenticationSuccessful
}

fun reauthenticateWithQ(project: Project) {
    val connection = ToolkitConnectionManager.getInstance(project).activeConnectionForFeature(QConnection.getInstance())
    if (connection !is ManagedBearerSsoConnection) return
    ApplicationManager.getApplication().executeOnPooledThread {
        reauthConnectionIfNeeded(project, connection)
    }
}

fun requestCredentialsForExplorer(
    project: Project,
    initialConnectionCount: Int = getConnectionCount(),
    initialAuthConnections: String = getEnabledConnections(
        project
    ),
    isFirstInstance: Boolean = false,
    connectionInitiatedFromExplorer: Boolean = false
): Boolean {
    val authenticationDialog = SetupAuthenticationDialog(
        project,
        tabSettings = mapOf(
            SetupAuthenticationTabs.BUILDER_ID to AuthenticationTabSettings(
                disabled = true,
                notice = SetupAuthenticationNotice(
                    SetupAuthenticationNotice.NoticeType.ERROR,
                    message("gettingstarted.setup.explorer.no_builder_id"),
                    "https://docs.aws.amazon.com/signin/latest/userguide/differences-aws_builder_id.html"
                )
            )
        ),
        promptForIdcPermissionSet = true,
        sourceOfEntry = SourceOfEntry.RESOURCE_EXPLORER,
        featureId = FeatureId.AwsExplorer,
        isFirstInstance = isFirstInstance,
        connectionInitiatedFromExplorer = connectionInitiatedFromExplorer
    )
    val isAuthSuccessful = authenticationDialog.showAndGet()
    if (isAuthSuccessful) {
        AuthTelemetry.addConnection(
            project,
            source = getSourceOfEntry(SourceOfEntry.RESOURCE_EXPLORER, isFirstInstance, connectionInitiatedFromExplorer),
            featureId = FeatureId.AwsExplorer,
            credentialSourceId = authenticationDialog.authType,
            isAggregated = true,
            attempts = authenticationDialog.attempts + 1,
            result = Result.Succeeded
        )
        AuthTelemetry.addedConnections(
            project,
            source = getSourceOfEntry(SourceOfEntry.RESOURCE_EXPLORER, isFirstInstance, connectionInitiatedFromExplorer),
            authConnectionsCount = initialConnectionCount,
            newAuthConnectionsCount = getConnectionCount() - initialConnectionCount,
            enabledAuthConnections = initialAuthConnections,
            newEnabledAuthConnections = getEnabledConnections(project),
            attempts = authenticationDialog.attempts + 1,
            result = Result.Succeeded
        )
    } else {
        AuthTelemetry.addConnection(
            project,
            source = getSourceOfEntry(SourceOfEntry.RESOURCE_EXPLORER, isFirstInstance, connectionInitiatedFromExplorer),
            featureId = FeatureId.AwsExplorer,
            credentialSourceId = authenticationDialog.authType,
            isAggregated = false,
            attempts = authenticationDialog.attempts + 1,
            result = Result.Cancelled,
        )
    }
    return isAuthSuccessful
}

internal fun ssoErrorMessageFromException(e: Exception) = when (e) {
    is IllegalStateException -> e.message ?: message("general.unknown_error")
    is ProcessCanceledException -> message("codewhisperer.credential.login.dialog.exception.cancel_login")
    is InvalidGrantException -> message("codewhisperer.credential.login.exception.invalid_grant")
    is InvalidRequestException -> message("codewhisperer.credential.login.exception.invalid_input")
    is SsoOidcException -> message("codewhisperer.credential.login.exception.general.oidc")
    else -> {
        val baseMessage = when (e) {
            is IOException -> "codewhisperer.credential.login.exception.io"
            else -> "codewhisperer.credential.login.exception.general"
        }

        message(baseMessage, "${e.javaClass.name}: ${e.message}")
    }
}

internal fun authAndUpdateConfig(
    project: Project,
    profile: UserConfigSsoSessionProfile,
    configFilesFacade: ConfigFilesFacade,
    onError: (String) -> Unit
): BearerSsoConnection? {
    val connection = try {
        ToolkitAuthManager.getInstance().tryCreateTransientSsoConnection(profile) {
            reauthConnectionIfNeeded(project, it)
        }
    } catch (e: Exception) {
        val message = ssoErrorMessageFromException(e)

        onError(message)
        LOG.error(e) { "Failed to authenticate: message: $message; profile: $profile" }
        return null
    }

    configFilesFacade.updateSectionInConfig(
        SsoSessionConstants.SSO_SESSION_SECTION_NAME,
        Profile.builder()
            .name(profile.configSessionName)
            .properties(
                mapOf(
                    "sso_start_url" to profile.startUrl,
                    "sso_region" to profile.ssoRegion,
                    "sso_registration_scopes" to profile.scopes.joinToString(",")
                )
            ).build()
    )

    return connection
}

fun deleteSsoConnectionCW(connection: AwsBearerTokenConnection) =
    deleteSsoConnection(getSsoSessionProfileNameFromBearer(connection))

fun deleteSsoConnectionExplorer(connection: CredentialIdentifier) =
    deleteSsoConnection(getSsoSessionProfileNameFromCredentials(connection))

fun deleteSsoConnection(sessionName: String) = DefaultConfigFilesFacade().deleteSsoConnectionFromConfig(sessionName)

fun getSsoSessionProfileNameFromBearer(connection: AwsBearerTokenConnection): String =
    connection.id.substringAfter("${SsoSessionConstants.SSO_SESSION_SECTION_NAME}:")

fun getSsoSessionProfileNameFromCredentials(connection: CredentialIdentifier): String {
    connection as ProfileCredentialsIdentifierSso
    return connection.ssoSessionName
}

const val CODEWHISPERER_AUTH_LEARN_MORE_LINK = "https://docs.aws.amazon.com/codewhisperer/latest/userguide/codewhisperer-auth.html"
