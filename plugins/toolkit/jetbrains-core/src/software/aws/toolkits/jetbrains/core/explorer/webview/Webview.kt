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
import kotlinx.coroutines.launch
import org.cef.CefApp
import software.aws.toolkits.core.credentials.ToolkitBearerTokenProvider
import software.aws.toolkits.core.region.AwsRegion
import software.aws.toolkits.jetbrains.core.coroutines.projectCoroutineScope
import software.aws.toolkits.jetbrains.core.credentials.AwsConnectionManager
import software.aws.toolkits.jetbrains.core.credentials.ConnectionSettingsStateChangeNotifier
import software.aws.toolkits.jetbrains.core.credentials.ConnectionState
import software.aws.toolkits.jetbrains.core.credentials.DefaultConfigFilesFacade
import software.aws.toolkits.jetbrains.core.credentials.Login
import software.aws.toolkits.jetbrains.core.credentials.ManagedBearerSsoConnection
import software.aws.toolkits.jetbrains.core.credentials.ToolkitAuthManager
import software.aws.toolkits.jetbrains.core.credentials.ToolkitConnection
import software.aws.toolkits.jetbrains.core.credentials.ToolkitConnectionManager
import software.aws.toolkits.jetbrains.core.credentials.ToolkitConnectionManagerListener
import software.aws.toolkits.jetbrains.core.credentials.UserConfigSsoSessionProfile
import software.aws.toolkits.jetbrains.core.credentials.authAndUpdateConfig
import software.aws.toolkits.jetbrains.core.credentials.sono.CODECATALYST_SCOPES
import software.aws.toolkits.jetbrains.core.credentials.sono.IDENTITY_CENTER_ROLE_ACCESS_SCOPE
import software.aws.toolkits.jetbrains.core.credentials.sono.SONO_REGION
import software.aws.toolkits.jetbrains.core.credentials.sono.SONO_URL
import software.aws.toolkits.jetbrains.core.credentials.sso.PendingAuthorization
import software.aws.toolkits.jetbrains.core.credentials.sso.bearer.InteractiveBearerTokenProvider
import software.aws.toolkits.jetbrains.core.explorer.AwsToolkitExplorerToolWindow
import software.aws.toolkits.jetbrains.core.explorer.isToolkitConnected
import software.aws.toolkits.jetbrains.core.explorer.showExplorerTree
import software.aws.toolkits.jetbrains.core.gettingstarted.IdcRolePopup
import software.aws.toolkits.jetbrains.core.gettingstarted.IdcRolePopupState
import software.aws.toolkits.jetbrains.core.gettingstarted.editor.getConnectionCount
import software.aws.toolkits.jetbrains.core.gettingstarted.editor.getEnabledConnections
import software.aws.toolkits.jetbrains.core.region.AwsRegionProvider
import software.aws.toolkits.jetbrains.core.webview.WebviewResourceHandlerFactory
import software.aws.toolkits.jetbrains.isDeveloperMode
import software.aws.toolkits.jetbrains.utils.pollFor
import software.aws.toolkits.telemetry.*
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
                it.init()
            }
        }
    }



    companion object {
        fun getInstance(project: Project) = project.service<ToolkitWebviewPanel>()
    }
}

// TODO: STILL WIP thus duplicate code / pending move to plugins/toolkit
class ToolkitWebviewBrowser(val project: Project) {
    val jcefBrowser: JBCefBrowserBase
    val query: JBCefJSQuery

    init {
        val client = JBCefApp.getInstance().createClient()
        Disposer.register(project, client)
        jcefBrowser = JBCefBrowserBuilder()
            .setClient(client)
            .setOffScreenRendering(true)
            .setCreateImmediately(true)
            .build()

        query = JBCefJSQuery.create(jcefBrowser as JBCefBrowserBase)
    }

    fun init() {
        CefApp.getInstance()
            .registerSchemeHandlerFactory(
                "http",
                ToolkitWebviewBrowser.DOMAIN,
                WebviewResourceHandlerFactory(
                    domain = "http://$DOMAIN/",
                    assetUri = "/webview/assets/"
                ),
            )

        loadWebView()
        var currentAuthorization: PendingAuthorization? = null

        val handler = Function<String, JBCefJSQuery.Response> {
            val command = jacksonObjectMapper().readTree(it).get("command").asText()
            println("command received from the browser: $command")

            when (command) {
                "fetchLastLoginIdcInfo" -> {
                    val lastLoginIdcInfo = ToolkitAuthManager.getInstance().getLastLoginIdcInfo()

                    val profileName = lastLoginIdcInfo.profileName
                    val startUrl = lastLoginIdcInfo.startUrl
                    val directoryId = extractDirectoryIdFromStartUrl(startUrl)
                    val region = lastLoginIdcInfo.region

                    executeJS("window.ideClient.updateLastLoginIdcInfo({" +
                        "profileName: \"$profileName\"," +
                        "directoryId: \"$directoryId\"," +
                        "region: \"$region\"})")
                }

                "fetchSsoRegion" -> {
                    val regions = AwsRegionProvider.getInstance().allRegionsForService("sso").values
                    val json = jacksonObjectMapper().writeValueAsString(regions)

                    executeJS("window.ideClient.updateSsoRegions($json)")
                }

                "loginBuilderId" -> {
                    val onPending: () -> Unit = {
                        projectCoroutineScope(project).launch {
                            val conn = pollForConnection(ToolkitBearerTokenProvider.ssoIdentifier(SONO_URL, SONO_REGION))

                            conn?.let { c ->
                                val provider = (c as ManagedBearerSsoConnection).getConnectionSettings().tokenProvider.delegate
                                val authorization = pollForAuthorization(provider as InteractiveBearerTokenProvider)

                                if (authorization != null) {
                                    executeJS("window.ideClient.updateAuthorization(\"${userCodeFromAuthorization(authorization)}\")")
                                    currentAuthorization = authorization
                                    return@launch
                                }
                            }
                        }
                    }

                    runInEdt {
                        Login.BuilderId(CODECATALYST_SCOPES, onPending).loginBuilderId(project)
                    }
                }

                "loginIdC" -> {
                    val profileName = jacksonObjectMapper().readTree(it).get("profileName").asText()
                    val url = jacksonObjectMapper().readTree(it).get("url").asText()
                    val region = jacksonObjectMapper().readTree(it).get("region").asText()
                    val awsRegion = AwsRegionProvider.getInstance()[region] ?: return@Function null

                    val feature: String = jacksonObjectMapper().readTree(it).get("feature").asText()

                    val onPendingToken: (InteractiveBearerTokenProvider) -> Unit = { provider ->
                        projectCoroutineScope(project).launch {
                            val authorization = pollForAuthorization(provider)
                            if (authorization != null) {
                                executeJS("window.ideClient.updateAuthorization(\"${userCodeFromAuthorization(authorization)}\")")
                                currentAuthorization = authorization
                            }
                        }
                    }
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
                            onPendingToken,
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
                    AwsTelemetry.loginWithBrowser(project = null, result = Result.Cancelled, credentialType = CredentialType.BearerToken)

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

        query.addHandler(handler)
    }

    fun updateState() {
        val isConnected = isToolkitConnected(project)
        executeJS("window.ideClient.updateIsConnected($isConnected)")
    }

    private fun executeJS(jsScript: String) {
        this.jcefBrowser.cefBrowser.let {
            it.executeJavaScript(jsScript, it.url, 0)
        }
    }

    private fun userCodeFromAuthorization(authorization: PendingAuthorization) = when (authorization) {
        is PendingAuthorization.DAGAuthorization -> authorization.authorization.userCode
        else -> ""
    }

    private fun extractDirectoryIdFromStartUrl(startUrl: String): String {
        val pattern = "https://(.*?).awsapps.com/start.*".toRegex()
        return pattern.matchEntire(startUrl)?.groupValues?.get(1).orEmpty()
    }

    fun component(): JComponent? = jcefBrowser.component

    fun resetBrowserState() {
        executeJS("window.ideClient.reset()")
    }

    private suspend fun pollForConnection(connectionId: String): ToolkitConnection? = pollFor {
        ToolkitAuthManager.getInstance().getConnection(connectionId)
    }

    private suspend fun pollForAuthorization(provider: InteractiveBearerTokenProvider): PendingAuthorization? = pollFor {
        provider.pendingAuthorization
    }

    private fun loadWebView() {
        // load the web app
        jcefBrowser.loadHTML(getWebviewHTML())
    }

    private fun getWebviewHTML(): String {
        val colorMode = if (JBColor.isBright()) "jb-light" else "jb-dark"
        val postMessageToJavaJsCode = query.inject("JSON.stringify(message)")

        val jsScripts = """
            <script type="text/javascript" src="$WEB_SCRIPT_URI"></script>
            <script>
                (function() {
                    window.ideApi = {
                     postMessage: message => {
                         $postMessageToJavaJsCode
                     }
                };
                }())
            </script>
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

// TODO:
fun requestCredentialsForExplorer(
    project: Project,
    profileName: String,
    startUrl: String,
    region: AwsRegion,
    scopes: List<String>,
    onPendingToken: (InteractiveBearerTokenProvider) -> Unit,
    onError: (String) -> Unit,
    promptForIdcPermissionSet: Boolean = true,
    initialConnectionCount: Int = getConnectionCount(),
    initialAuthConnections: String = getEnabledConnections(
        project
    ),
    isFirstInstance: Boolean = false,
    connectionInitiatedFromExplorer: Boolean = false
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
