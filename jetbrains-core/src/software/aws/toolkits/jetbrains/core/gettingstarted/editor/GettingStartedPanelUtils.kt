// Copyright 2023 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.core.gettingstarted.editor

import com.intellij.openapi.project.Project
import com.intellij.ui.dsl.builder.Panel
import software.aws.toolkits.core.credentials.CredentialIdentifier
import software.aws.toolkits.jetbrains.core.credentials.AwsBearerTokenConnection
import software.aws.toolkits.jetbrains.core.credentials.AwsConnectionManager
import software.aws.toolkits.jetbrains.core.credentials.ConnectionState
import software.aws.toolkits.jetbrains.core.credentials.CredentialManager
import software.aws.toolkits.jetbrains.core.credentials.ToolkitAuthManager
import software.aws.toolkits.jetbrains.core.credentials.ToolkitConnectionManager
import software.aws.toolkits.jetbrains.core.credentials.lazyIsUnauthedBearerConnection
import software.aws.toolkits.jetbrains.core.credentials.pinning.CodeCatalystConnection
import software.aws.toolkits.jetbrains.core.credentials.pinning.CodeWhispererConnection
import software.aws.toolkits.jetbrains.core.credentials.profiles.SsoSessionConstants
import software.aws.toolkits.jetbrains.core.credentials.sono.SONO_URL

data class ValidActiveIamConnection(
    val isConnectionValid: ValidConn,
    val activeConnection: CredentialIdentifier?,
    val connectionType: CWConnectionType?
)

data class ValidActiveConnection(
    val isConnectionValid: ValidConn,
    val activeConnection: AwsBearerTokenConnection?,
    val connectionType: CWConnectionType?
)

enum class CWConnectionType {
    BUILDER_ID,
    IAM_IDC,
    IAM,
    UNKNOWN
}

enum class ValidConn {
    EXPIRED,
    VALID,
    NOT_CONNECTED
}

fun controlPanelVisibility(currentPanel: Panel, newPanel: Panel) {
    currentPanel.visible(false)
    newPanel.visible(true)
}

fun checkBearerConnectionValidity(project: Project, source: String): ValidActiveConnection {
    val connections = ToolkitAuthManager.getInstance().listConnections().filterIsInstance<AwsBearerTokenConnection>()
    if (connections.size < 1) return ValidActiveConnection(ValidConn.NOT_CONNECTED, null, null)

    val activeConnection = if (source == "Codewhisperer") {
        ToolkitConnectionManager.getInstance(project).activeConnectionForFeature(CodeWhispererConnection.getInstance())
    } else if (source == "CodeCatalyst") {
        ToolkitConnectionManager.getInstance(project).activeConnectionForFeature(CodeCatalystConnection.getInstance())
    } else {
        ToolkitConnectionManager.getInstance(project).activeConnection()
    }
    if (activeConnection == null) return ValidActiveConnection(ValidConn.NOT_CONNECTED, null, null)
    activeConnection as AwsBearerTokenConnection
    val connectionType = if (activeConnection.startUrl == SONO_URL) CWConnectionType.BUILDER_ID else CWConnectionType.IAM_IDC
    if (activeConnection.lazyIsUnauthedBearerConnection()) {
        return ValidActiveConnection(ValidConn.EXPIRED, activeConnection, connectionType)
    } else {
        return ValidActiveConnection(ValidConn.VALID, activeConnection, connectionType)
    }
}

fun checkIamConnectionValidity(project: Project): ValidActiveIamConnection {
    val currConn = AwsConnectionManager.getInstance(project).selectedCredentialIdentifier ?: return ValidActiveIamConnection(
        ValidConn.NOT_CONNECTED,
        null,
        null
    )
    val invalidConnection = AwsConnectionManager.getInstance(project).connectionState.let { it.isTerminal && it !is ConnectionState.ValidConnection }
    return if (invalidConnection) {
        ValidActiveIamConnection(ValidConn.EXPIRED, currConn, isCredentialSso(currConn.shortName))
    } else {
        ValidActiveIamConnection(ValidConn.VALID, currConn, isCredentialSso(currConn.shortName))
    }
}

fun isCredentialSso(providerId: String): CWConnectionType {
    val profileName = providerId.split("-").first()
    val ssoSessionIds = CredentialManager.getInstance().getSsoSessionIdentifiers().map {
        it.id.substringAfter(
            "${SsoSessionConstants.SSO_SESSION_SECTION_NAME}:"
        )
    }
    return if (profileName in ssoSessionIds) CWConnectionType.IAM_IDC else CWConnectionType.IAM
}
