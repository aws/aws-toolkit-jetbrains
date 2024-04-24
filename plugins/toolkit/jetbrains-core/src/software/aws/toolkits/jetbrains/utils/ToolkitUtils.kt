// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.utils

import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.project.Project
import org.slf4j.LoggerFactory
import software.aws.toolkits.core.utils.debug
import software.aws.toolkits.jetbrains.core.credentials.AwsBearerTokenConnection
import software.aws.toolkits.jetbrains.core.credentials.CredentialManager
import software.aws.toolkits.jetbrains.core.credentials.ToolkitConnectionManager
import software.aws.toolkits.jetbrains.core.credentials.pinning.CodeCatalystConnection
import software.aws.toolkits.jetbrains.core.credentials.sono.IDENTITY_CENTER_ROLE_ACCESS_SCOPE
import software.aws.toolkits.jetbrains.core.explorer.AwsToolkitExplorerToolWindow
import software.aws.toolkits.jetbrains.core.explorer.webview.ToolkitWebviewPanel
import software.aws.toolkits.jetbrains.core.webview.BrowserState
import software.aws.toolkits.telemetry.FeatureId

private val LOG = LoggerFactory.getLogger("ToolkitUtils")

fun inspectExistingConnection(project: Project): Boolean =
    ToolkitConnectionManager.getInstance(project).let {
        if (CredentialManager.getInstance().getCredentialIdentifiers().isNotEmpty()) {
            LOG.debug { "inspecting existing connection and found IAM credentials" }
            return@let true
        }

        val conn = it.activeConnection()
        val hasIdCRoleAccess = if (conn is AwsBearerTokenConnection) {
            conn.scopes.contains(IDENTITY_CENTER_ROLE_ACCESS_SCOPE)
        } else {
            false
        }

        if (hasIdCRoleAccess) {
            LOG.debug { "inspecting existing connection and found bearer connections with IdCRoleAccess scope" }
            return@let true
        }

        val isCodecatalystConn = it.activeConnectionForFeature(CodeCatalystConnection.getInstance()) != null
        if (isCodecatalystConn) {
            LOG.debug { "inspecting existing connection and found active Codecatalyst connection" }
            return@let true
        }

        return@let false
    }


fun Project.reloadToolkitToolWindow(isToolkitConnected: Boolean?) {
    val isConnected = isToolkitConnected ?: inspectExistingConnection(this)
    this.toggleToolkitToolWindow(isBrowser = !isConnected)
}

fun Project.toggleToolkitToolWindow(isBrowser: Boolean) {
    val component = if (isBrowser) {
        ToolkitWebviewPanel.getInstance(this).let {
            it.browser?.prepareBrowser(BrowserState(FeatureId.AwsExplorer))
            it.component
        }
    } else {
        AwsToolkitExplorerToolWindow.getInstance(this)
    }

    val contentManager = AwsToolkitExplorerToolWindow.toolWindow(this).contentManager

    val myContent = contentManager.factory.createContent(component, null, false).also {
        it.isCloseable = true
        it.isPinnable = true
    }

    runInEdt {
        contentManager.removeAllContents(true)
        contentManager.addContent(myContent)
    }
}
