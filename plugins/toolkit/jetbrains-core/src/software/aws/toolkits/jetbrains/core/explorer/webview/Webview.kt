// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.core.explorer.webview

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.Disposer
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.components.panels.Wrapper
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.gridLayout.HorizontalAlign
import com.intellij.ui.dsl.gridLayout.VerticalAlign
import com.intellij.ui.jcef.JBCefApp
import com.intellij.ui.jcef.JBCefBrowserBase
import com.intellij.ui.jcef.JBCefBrowserBuilder
import com.intellij.ui.jcef.JBCefJSQuery
import org.cef.CefApp
import software.aws.toolkits.core.region.AwsRegion
import software.aws.toolkits.jetbrains.core.credentials.DefaultConfigFilesFacade
import software.aws.toolkits.jetbrains.core.credentials.Login
import software.aws.toolkits.jetbrains.core.credentials.ToolkitAuthManager
import software.aws.toolkits.jetbrains.core.credentials.ToolkitConnectionManager
import software.aws.toolkits.jetbrains.core.credentials.UserConfigSsoSessionProfile
import software.aws.toolkits.jetbrains.core.credentials.authAndUpdateConfig
import software.aws.toolkits.jetbrains.core.credentials.sono.CODECATALYST_SCOPES
import software.aws.toolkits.jetbrains.core.credentials.sono.IDENTITY_CENTER_ROLE_ACCESS_SCOPE
import software.aws.toolkits.jetbrains.core.credentials.sso.bearer.InteractiveBearerTokenProvider
import software.aws.toolkits.jetbrains.core.explorer.isToolkitConnected
import software.aws.toolkits.jetbrains.core.explorer.showExplorerTree
import software.aws.toolkits.jetbrains.core.gettingstarted.IdcRolePopup
import software.aws.toolkits.jetbrains.core.gettingstarted.IdcRolePopupState
import software.aws.toolkits.jetbrains.core.region.AwsRegionProvider
import software.aws.toolkits.jetbrains.core.webview.LoginBrowser
import software.aws.toolkits.jetbrains.core.webview.WebviewResourceHandlerFactory
import software.aws.toolkits.jetbrains.isDeveloperMode
import java.awt.event.ActionListener
import java.util.function.Function
import javax.swing.JButton
import javax.swing.JComponent

// This action is used to open the Q webview  development mode.
class OpenToolkitWebviewAction : DumbAwareAction("View Toolkit Webview") {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return

        WebviewDialog(project).showAndGet()
    }

    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = isDeveloperMode()
    }
}

private class WebviewDialog(private val project: Project) : DialogWrapper(project) {

    init {
        title = "Toolkit-Login-Webview"
        init()
    }

    override fun createCenterPanel(): JComponent = ToolkitWebviewPanel(project).component
}

class ToolkitWebviewPanel(val project: Project) {
    private val webviewContainer = Wrapper()
    var browser: ToolkitWebviewBrowser? = null
        private set

    val component = panel {
        row {
            cell(webviewContainer)
                .horizontalAlign(HorizontalAlign.FILL)
                .verticalAlign(VerticalAlign.FILL)
        }.resizableRow()

        row {
            cell(
                JButton("Show Web Debugger").apply {
                    addActionListener(
                        ActionListener {
                            browser?.jcefBrowser?.openDevtools()
                        },
                    )
                },
            )
                .horizontalAlign(HorizontalAlign.CENTER)
                .verticalAlign(VerticalAlign.BOTTOM)
        }
    }

    init {
        if (!JBCefApp.isSupported()) {
            // Fallback to an alternative browser-less solution
            webviewContainer.add(JBTextArea("JCEF not supported"))
            browser = null
        } else {
            browser = ToolkitWebviewBrowser(project).also {
                webviewContainer.add(it.component())
            }
        }
    }

    companion object {
        fun getInstance(project: Project) = project.service<ToolkitWebviewPanel>()
    }
}

// TODO: STILL WIP thus duplicate code / pending move to plugins/toolkit
class ToolkitWebviewBrowser(val project: Project) : LoginBrowser(project, ToolkitWebviewBrowser.DOMAIN) {
    override val jcefBrowser: JBCefBrowserBase by lazy {
        val client = JBCefApp.getInstance().createClient()
        Disposer.register(project, client)
        JBCefBrowserBuilder()
            .setClient(client)
            .setOffScreenRendering(true)
            .setCreateImmediately(true)
            .build()
    }
    override val query: JBCefJSQuery = JBCefJSQuery.create(jcefBrowser)

    override val handler = Function<String, JBCefJSQuery.Response> {
        val command = jacksonObjectMapper().readTree(it).get("command").asText()
        println("command received from the browser: $command")

        when (command) {
            "prepareUi" -> {
                this.prepareBrowser()
            }

            "loginBuilderId" -> {
                runInEdt {
                    Login.BuilderId(CODECATALYST_SCOPES, onPendingAwsId).loginBuilderId(project)
                }
            }

            "loginIdC" -> {
                val profileName = jacksonObjectMapper().readTree(it).get("profileName").asText()
                val url = jacksonObjectMapper().readTree(it).get("url").asText()
                val region = jacksonObjectMapper().readTree(it).get("region").asText()
                val awsRegion = AwsRegionProvider.getInstance()[region] ?: return@Function null

                val feature: String = jacksonObjectMapper().readTree(it).get("feature").asText()

                val onError: (String) -> Unit = { s ->
                    Messages.showErrorDialog(project, it, "Toolkit Idc Login Failed")
                    // TODO: AuthTelemetry.addConnection
                }

                val scope = if (feature == "CodeCatalyst") {
                    CODECATALYST_SCOPES
                } else {
                    listOf(IDENTITY_CENTER_ROLE_ACCESS_SCOPE)
                }

                runInEdt {
                    requestCredentialsForExplorer(
                        project,
                        profileName,
                        url,
                        awsRegion,
                        scope,
                        onPendingProfile,
                        onError
                    )
                }
            }

            "loginIAM" -> {
                // TODO: not type safe
                val profileName = jacksonObjectMapper().readTree(it).get("profileName").asText()
                val accessKey = jacksonObjectMapper().readTree(it).get("accessKey").asText()
                val secretKey = jacksonObjectMapper().readTree(it).get("secretKey").asText()

                // TODO:
                runInEdt {
                    Login.LongLivedIAM(
                        profileName,
                        accessKey,
                        secretKey
                    ).loginIAM(project, {}, {}, {})
                }
            }

            "toggleBrowser" -> {
                showExplorerTree(project)
            }

            "cancelLogin" -> {
                println("cancel login........")
                // TODO: BearerToken vs. SsoProfile
//                  TODO:   AwsTelemetry.loginWithBrowser

                // Essentially Authorization becomes a mutable that allows browser and auth to communicate canceled
                // status. There might be a risk of race condition here by changing this global, for which effort
                // has been made to avoid it (e.g. Cancel button is only enabled if Authorization has been given
                // to browser.). The worst case is that the user will see a stale user code displayed, but not
                // affecting the current login flow.
                currentAuthorization?.progressIndicator?.cancel()
            }

            else -> {
                error("received unknown command from Toolkit login browser")
            }
        }

        null
    }

    init {
        CefApp.getInstance()
            .registerSchemeHandlerFactory(
                "http",
                domain,
                WebviewResourceHandlerFactory(
                    domain = "http://$domain/",
                    assetUri = "/webview/assets/"
                ),
            )

        loadWebView()

        query.addHandler(handler)
    }

    override fun prepareBrowser() {
        // previous login
        val lastLoginIdcInfo = ToolkitAuthManager.getInstance().getLastLoginIdcInfo()
        val profileName = lastLoginIdcInfo.profileName
        val startUrl = lastLoginIdcInfo.startUrl
        val region = lastLoginIdcInfo.region

        // available regions
        val regions = AwsRegionProvider.getInstance().allRegionsForService("sso").values
        val regionJson = jacksonObjectMapper().writeValueAsString(regions)

        val isConnected = isToolkitConnected(project)
        val jsonData = """
            {
                stage: 'START',
                regions: $regionJson,
                idcInfo: {
                    profileName: '$profileName',
                    startUrl: '$startUrl',
                    region: '$region'
                },
                isConnected: $isConnected
            }
        """.trimIndent()
        executeJS("window.ideClient.prepareUi($jsonData)")
    }

    private fun extractDirectoryIdFromStartUrl(startUrl: String): String {
        val pattern = "https://(.*?).awsapps.com/start.*".toRegex()
        return pattern.matchEntire(startUrl)?.groupValues?.get(1).orEmpty()
    }

    fun component(): JComponent? = jcefBrowser.component

    override fun getWebviewHTML(): String {
        val colorMode = if (JBColor.isBright()) "jb-light" else "jb-dark"
        val postMessageToJavaJsCode = query.inject("JSON.stringify(message)")

        val jsScripts = """
            <script>
                (function() {
                    window.ideApi = {
                     postMessage: message => {
                         $postMessageToJavaJsCode
                     }
                };
                }())
            </script>
            <script type="text/javascript" src="$WEB_SCRIPT_URI"></script>
        """.trimIndent()

        return """
            <!DOCTYPE html>
            <html>
                <head>
                    <title>AWS Q</title>
                </head>
                <body class="$colorMode">
                    <div id="app"></div>
                    $jsScripts
                </body>
            </html>
        """.trimIndent()
    }

    companion object {
        private const val WEB_SCRIPT_URI = "http://webview/js/toolkitGetStart.js"
        private const val DOMAIN = "webview"
    }
}

fun requestCredentialsForExplorer(
    project: Project,
    profileName: String,
    startUrl: String,
    region: AwsRegion,
    scopes: List<String>,
    onPendingToken: (InteractiveBearerTokenProvider) -> Unit,
    onError: (String) -> Unit,
    promptForIdcPermissionSet: Boolean = true,
): Boolean {
    val configFilesFacade = DefaultConfigFilesFacade()
    try {
        configFilesFacade.readSsoSessions()
    } catch (e: Exception) {
        return false
    }

    val profile = UserConfigSsoSessionProfile(
        configSessionName = profileName,
        ssoRegion = region.id,
        startUrl = startUrl,
        scopes = scopes
    )

    val connection = authAndUpdateConfig(project, profile, configFilesFacade, onPendingToken, onError) ?: return false
    ToolkitConnectionManager.getInstance(project).switchConnection(connection)

    if (!promptForIdcPermissionSet) {
        return true
    }

    val tokenProvider = connection.getConnectionSettings().tokenProvider

    val rolePopup = IdcRolePopup(
        project,
        region.id,
        profileName,
        tokenProvider,
        IdcRolePopupState(), // TODO: is it correct state ?
        configFilesFacade = configFilesFacade,
    )

    if (!rolePopup.showAndGet()) {
        // don't close window if role is needed but was not confirmed
        // TODO: ??
        return false
    }

    return true
}
