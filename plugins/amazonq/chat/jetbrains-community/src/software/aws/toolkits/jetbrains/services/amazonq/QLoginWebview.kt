// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonq

import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.components.panels.Wrapper
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.gridLayout.HorizontalAlign
import com.intellij.ui.dsl.gridLayout.VerticalAlign
import com.intellij.ui.jcef.JBCefApp
import com.intellij.ui.jcef.JBCefJSQuery
import org.cef.CefApp
import software.aws.toolkits.core.region.AwsRegion
import software.aws.toolkits.core.utils.error
import software.aws.toolkits.core.utils.getLogger
import software.aws.toolkits.core.utils.warn
import software.aws.toolkits.jetbrains.core.credentials.AwsBearerTokenConnection
import software.aws.toolkits.jetbrains.core.credentials.ToolkitAuthManager
import software.aws.toolkits.jetbrains.core.credentials.ToolkitConnection
import software.aws.toolkits.jetbrains.core.credentials.ToolkitConnectionManager
import software.aws.toolkits.jetbrains.core.credentials.actions.SsoLogoutAction
import software.aws.toolkits.jetbrains.core.credentials.pinning.QConnection
import software.aws.toolkits.jetbrains.core.credentials.sono.Q_SCOPES
import software.aws.toolkits.jetbrains.core.credentials.sono.SONO_URL
import software.aws.toolkits.jetbrains.core.credentials.sono.isSono
import software.aws.toolkits.jetbrains.core.credentials.sso.bearer.InteractiveBearerTokenProvider
import software.aws.toolkits.jetbrains.core.credentials.ssoErrorMessageFromException
import software.aws.toolkits.jetbrains.core.region.AwsRegionProvider
import software.aws.toolkits.jetbrains.core.webview.AwsLoginBrowser
import software.aws.toolkits.jetbrains.core.webview.BearerLoginHandler
import software.aws.toolkits.jetbrains.core.webview.BrowserMessage
import software.aws.toolkits.jetbrains.core.webview.BrowserState
import software.aws.toolkits.jetbrains.core.webview.WebviewResourceHandlerFactory
import software.aws.toolkits.jetbrains.isDeveloperMode
import software.aws.toolkits.jetbrains.services.amazonq.util.createBrowser
import software.aws.toolkits.jetbrains.utils.isQExpired
import software.aws.toolkits.telemetry.AwsTelemetry
import software.aws.toolkits.telemetry.CredentialSourceId
import software.aws.toolkits.telemetry.FeatureId
import software.aws.toolkits.telemetry.Result
import software.aws.toolkits.telemetry.WebviewTelemetry
import java.awt.event.ActionListener
import javax.swing.JButton

@Service(Service.Level.PROJECT)
class QWebviewPanel private constructor(val project: Project) : Disposable {
    private val webviewContainer = Wrapper()
    var browser: QWebviewBrowser? = null
        private set

    val component = panel {
        row {
            cell(webviewContainer)
                .horizontalAlign(HorizontalAlign.FILL)
                .verticalAlign(VerticalAlign.FILL)
        }.resizableRow()

        if (isDeveloperMode()) {
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
    }

    init {
        if (!JBCefApp.isSupported()) {
            // Fallback to an alternative browser-less solution
            webviewContainer.add(JBTextArea("JCEF not supported"))
            browser = null
        } else {
            browser = QWebviewBrowser(project, this).also {
                webviewContainer.add(it.jcefBrowser.component)
            }
        }
    }

    companion object {
        fun getInstance(project: Project) = project.service<QWebviewPanel>()
    }

    override fun dispose() {
    }
}

class QWebviewBrowser(val project: Project, private val parentDisposable: Disposable) : AwsLoginBrowser(
    project,
    QWebviewBrowser.DOMAIN,
    QWebviewBrowser.WEB_SCRIPT_URI
) {
    // TODO: confirm if we need such configuration or the default is fine
    override val jcefBrowser = createBrowser(parentDisposable)
    private val query = JBCefJSQuery.create(jcefBrowser)

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

        loadWebView(query)

        query.addHandler(jcefHandler)
    }

    override fun handleBrowserMessage(message: BrowserMessage?) {
        when (message) {
            is BrowserMessage.PrepareUi -> {
                this.prepareBrowser(BrowserState(FeatureId.Q, browserCancellable = false))
                WebviewTelemetry.amazonqSignInOpened(
                    project,
                    reAuth = isQExpired(project)
                )
            }

            is BrowserMessage.SelectConnection -> {
                myState.existingConnections.firstOrNull { it.id == message.connectionId }?.let { conn ->
                    if (conn.isSono()) {
                        loginBuilderId(Q_SCOPES)
                    } else {
                        // TODO: rewrite scope logic, it's short term solution only
                        AwsRegionProvider.getInstance()[conn.region]?.let { region ->
                            loginIdC(conn.startUrl, region, Q_SCOPES)
                        }
                    }
                }
            }

            is BrowserMessage.LoginBuilderId -> {
                loginBuilderId(Q_SCOPES)
            }

            is BrowserMessage.LoginIdC -> {
                val awsRegion = AwsRegionProvider.getInstance()[message.region] ?: error("unknown region returned from Q browser")
                val scopes = Q_SCOPES

                loginIdC(message.url, awsRegion, scopes)
            }

            is BrowserMessage.CancelLogin -> {
                cancelLogin()
            }

            is BrowserMessage.Signout -> {
                (
                    ToolkitConnectionManager.getInstance(project)
                        .activeConnectionForFeature(QConnection.getInstance()) as? AwsBearerTokenConnection
                    )?.let { connection ->
                    SsoLogoutAction(connection).actionPerformed(
                        AnActionEvent.createFromDataContext(
                            "qBrowser",
                            null,
                            DataContext.EMPTY_CONTEXT
                        )
                    )
                }
            }

            is BrowserMessage.Reauth -> {
                reauth(ToolkitConnectionManager.getInstance(project).activeConnectionForFeature(QConnection.getInstance()))
            }

            else -> {
                LOG.warn { "received unknown command from Q browser, unable to de-serialized" }
            }
        }
    }

    override fun customize(state: BrowserState): BrowserState {
        state.existingConnections = ToolkitAuthManager.getInstance().listConnections()
            .filterIsInstance<AwsBearerTokenConnection>()

        state.stage = if (isQExpired(project)) {
            "REAUTH"
        } else {
            "START"
        }

        return state
    }

    override fun loginBuilderId(scopes: List<String>) {
        val h = object : BearerLoginHandler {
            override fun onSuccess(connection: ToolkitConnection) {
                AwsTelemetry.loginWithBrowser(
                    project = null,
                    credentialStartUrl = SONO_URL,
                    result = Result.Succeeded,
                    credentialSourceId = CredentialSourceId.AwsId
                )
            }

            override fun onPendingToken(provider: InteractiveBearerTokenProvider) {
                updateOnPendingToken(provider)
            }

            override fun onError(e: Exception) {
                tryHandleUserCanceledLogin(e)
                AwsTelemetry.loginWithBrowser(
                    project = null,
                    credentialStartUrl = SONO_URL,
                    result = Result.Failed,
                    reason = e.message,
                    credentialSourceId = CredentialSourceId.AwsId
                )
            }
        }

        loginBuilderId(scopes, h)
    }

    override fun loginIdC(url: String, region: AwsRegion, scopes: List<String>) {
        val h = object : BearerLoginHandler {
            override fun onPendingToken(provider: InteractiveBearerTokenProvider) {
                updateOnPendingToken(provider)
            }

            override fun onSuccess(connection: ToolkitConnection) {
                AwsTelemetry.loginWithBrowser(
                    project = null,
                    credentialStartUrl = url,
                    result = Result.Succeeded,
                    credentialSourceId = CredentialSourceId.IamIdentityCenter
                )
            }

            override fun onError(e: Exception) {
                val message = ssoErrorMessageFromException(e)
                if (!tryHandleUserCanceledLogin(e)) {
                    LOG.error(e) { "Failed to authenticate: message: $message" }
                }

                AwsTelemetry.loginWithBrowser(
                    project = null,
                    credentialStartUrl = url,
                    result = Result.Failed,
                    reason = e.message,
                    credentialSourceId = CredentialSourceId.IamIdentityCenter
                )
            }
        }

        loginIdC(url, region, scopes, h)
    }

    override fun loadWebView(query: JBCefJSQuery) {
        jcefBrowser.loadHTML(getWebviewHTML(webScriptUri, query))
    }

    companion object {
        private val LOG = getLogger<QWebviewBrowser>()
        private const val WEB_SCRIPT_URI = "http://webview/js/getStart.js"
        private const val DOMAIN = "webview"
    }
}
