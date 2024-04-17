// Copyright 2022 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.core.explorer

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.openapi.wm.ex.ToolWindowEx
import software.aws.toolkits.core.utils.debug
import software.aws.toolkits.core.utils.getLogger
import software.aws.toolkits.jetbrains.AwsToolkit
import software.aws.toolkits.jetbrains.core.credentials.AwsBearerTokenConnection
import software.aws.toolkits.jetbrains.core.credentials.AwsConnectionManager
import software.aws.toolkits.jetbrains.core.credentials.AwsConnectionManagerConnection
import software.aws.toolkits.jetbrains.core.credentials.ConnectionSettingsStateChangeNotifier
import software.aws.toolkits.jetbrains.core.credentials.ConnectionState
import software.aws.toolkits.jetbrains.core.credentials.CredentialManager
import software.aws.toolkits.jetbrains.core.credentials.ToolkitConnection
import software.aws.toolkits.jetbrains.core.credentials.ToolkitConnectionManager
import software.aws.toolkits.jetbrains.core.credentials.ToolkitConnectionManagerListener
import software.aws.toolkits.jetbrains.core.credentials.pinning.CodeCatalystConnection
import software.aws.toolkits.jetbrains.core.credentials.sono.CODECATALYST_SCOPES
import software.aws.toolkits.jetbrains.core.credentials.sono.IDENTITY_CENTER_ROLE_ACCESS_SCOPE
import software.aws.toolkits.jetbrains.core.experiments.ExperimentsActionGroup
import software.aws.toolkits.jetbrains.core.explorer.webview.ToolkitWebviewPanel
import software.aws.toolkits.jetbrains.core.help.HelpIds
import software.aws.toolkits.jetbrains.utils.actions.OpenBrowserAction
import software.aws.toolkits.resources.message
import software.aws.toolkits.telemetry.FeatureId
import java.util.concurrent.atomic.AtomicBoolean

class AwsToolkitExplorerFactory : ToolWindowFactory, DumbAware {
    private val isConnected = AtomicBoolean(false)

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        toolWindow.helpId = HelpIds.EXPLORER_WINDOW.id

        if (toolWindow is ToolWindowEx) {
            val actionManager = ActionManager.getInstance()
            toolWindow.setTitleActions(listOf(actionManager.getAction("aws.toolkit.explorer.titleBar")))
            toolWindow.setAdditionalGearActions(
                DefaultActionGroup().apply {
                    add(
                        OpenBrowserAction(
                            title = message("explorer.view_documentation"),
                            url = AwsToolkit.AWS_DOCS_URL
                        )
                    )
                    add(
                        OpenBrowserAction(
                            title = message("explorer.view_source"),
                            icon = AllIcons.Vcs.Vendors.Github,
                            url = AwsToolkit.GITHUB_URL
                        )
                    )
                    add(
                        OpenBrowserAction(
                            title = message("explorer.create_new_issue"),
                            icon = AllIcons.Vcs.Vendors.Github,
                            url = "${AwsToolkit.GITHUB_URL}/issues/new/choose"
                        )
                    )
                    add(actionManager.getAction("aws.toolkit.showFeedback"))
                    add(ExperimentsActionGroup())
                    add(actionManager.getAction("aws.settings.show"))
                }
            )
        }

        val contentManager = toolWindow.contentManager

        val content = contentManager.factory.createContent(ToolkitWebviewPanel.getInstance(project).component, null, false).also {
            it.isCloseable = true
            it.isPinnable = true
        }
        contentManager.addContent(content)
        toolWindow.activate(null)
        contentManager.setSelectedContent(content)

        project.messageBus.connect().subscribe(
            ToolkitConnectionManagerListener.TOPIC,
            object : ToolkitConnectionManagerListener {
                override fun activeConnectionChanged(newConnection: ToolkitConnection?) {
                    getLogger<AwsToolkitExplorerFactory>().debug { "activeConnectionChanged: isToolkitConnected=${isToolkitConnected(project)}" }
                    connectionChanged(project, newConnection, toolWindow)
                }
            }
        )

        project.messageBus.connect().subscribe(
            AwsConnectionManager.CONNECTION_SETTINGS_STATE_CHANGED,
            object : ConnectionSettingsStateChangeNotifier {
                override fun settingsStateChanged(newState: ConnectionState) {
                    getLogger<AwsToolkitExplorerFactory>().debug { "settingsStateChanged: isToolkitConnected=${isToolkitConnected(project)}" }
                    connectionChanged(project, ToolkitConnectionManager.getInstance(project).activeConnection(), toolWindow)
                }
            }
        )
    }

    override fun init(toolWindow: ToolWindow) {
        toolWindow.stripeTitle = message("aws.notification.title")
    }

    private fun connectionChanged(project: Project, newConnection: ToolkitConnection?, toolWindow: ToolWindow) {
        val isToolkitConnected = when (newConnection) {
            is AwsConnectionManagerConnection -> {
                true
            }

            is AwsBearerTokenConnection -> {
                CODECATALYST_SCOPES.all { it in newConnection.scopes } ||
                    newConnection.scopes.contains(IDENTITY_CENTER_ROLE_ACCESS_SCOPE) ||
                    CredentialManager.getInstance().getCredentialIdentifiers().isNotEmpty()
            }

            null -> {
                ToolkitConnectionManager.getInstance(project).let {
                    val conn = it.activeConnection()
                    val hasIamConn = if (conn is AwsBearerTokenConnection) {
                        conn.scopes.contains(IDENTITY_CENTER_ROLE_ACCESS_SCOPE)
                    } else false

                    it.activeConnectionForFeature(CodeCatalystConnection.getInstance()) != null || hasIamConn
                }
            }

            else -> {
                false
            }
        }

        val old = isConnected.getAndSet(isToolkitConnected)
        // TODO: weird to put it here and need to double check can we wrap it into Browser.prepareUi()
        ToolkitWebviewPanel.getInstance(project).browser?.executeJS("window.ideClient.updateIsConnected($isToolkitConnected)")
        if (old == isToolkitConnected) {
            return
        }

        val contentManager = toolWindow.contentManager
        val component = if (isToolkitConnected) {
            getLogger<AwsToolkitExplorerFactory>().debug { "Rendering explorer tree" }
            AwsToolkitExplorerToolWindow.getInstance(project)
        } else {
            getLogger<AwsToolkitExplorerFactory>().debug { "Rendering signin webview" }
            ToolkitWebviewPanel.getInstance(project).let {
                it.browser?.prepareBrowser(FeatureId.AwsExplorer)
                it.component
            }
        }
        val myContent = contentManager.factory.createContent(component, null, false).also {
            it.isCloseable = true
            it.isPinnable = true
        }

        runInEdt {
            contentManager.removeAllContents(true)
            contentManager.addContent(myContent)
        }
    }

    companion object {
        const val TOOLWINDOW_ID = "aws.toolkit.explorer"
    }
}

// TODO: should move to somewhere else and public ?
/**
 * (1)
 *     if (there is an IAM profile) {
 *         // show explorer
 *     }
 *
 * (2)
 *     else if (there is any IdC SSO session) { // implies no iam profile
 *        // 2a: has codecatlyst connection pinned
 *
 *
 *        // 2b: user selects desired sso session from list
 *
 *        if (session does not have sso:account:access)
 *            // 2bii: request scope upgrade
 *        // 2c: request permission set selection
 *     }
 */
fun isToolkitConnected(project: Project): Boolean {
    // (1)
    if (AwsConnectionManager.getInstance(project).isValidConnectionSettings()) {
        getLogger<AwsToolkitExplorerFactory>().debug {
            "isToolkitConnected returns true: AwsConnectionManager.getInstance(project).isValidConnectionSettings"
        }
        return true
    }

    val bearerCredsManager = ToolkitConnectionManager.getInstance(project)
    // (2a) has codecatlyst connection (either pinned or not pinned)
    if (bearerCredsManager.isFeatureEnabled(CodeCatalystConnection.getInstance())) {
        getLogger<AwsToolkitExplorerFactory>().debug {
            "isToolkitConnected returns true: isFeatureEnabled(CodeCatalyst)"
        }
        return true
    }

    return bearerCredsManager.activeConnection()?.let { bearerConn ->
        if (bearerConn is AwsBearerTokenConnection) {
            getLogger<AwsToolkitExplorerFactory>().debug { "isToolkitConnected returns true: active IdentityRoleAccess connection" }
            bearerConn.scopes.contains(IDENTITY_CENTER_ROLE_ACCESS_SCOPE)
        } else {
            false
        }
    } ?: false
}

fun showWebview(project: Project) {
    val contentManager = AwsToolkitExplorerToolWindow.toolWindow(project).contentManager

    val myContent = contentManager.factory.createContent(ToolkitWebviewPanel.getInstance(project).component, null, false).also {
        it.isCloseable = true
        it.isPinnable = true
    }

    runInEdt {
        contentManager.removeAllContents(true)
        contentManager.addContent(myContent)
    }
}

fun showExplorerTree(project: Project) {
    val contentManager = AwsToolkitExplorerToolWindow.toolWindow(project).contentManager

    val myContent = contentManager.factory.createContent(AwsToolkitExplorerToolWindow.getInstance(project), null, false).also {
        it.isCloseable = true
        it.isPinnable = true
    }

    runInEdt {
        contentManager.removeAllContents(true)
        contentManager.addContent(myContent)
    }
}
