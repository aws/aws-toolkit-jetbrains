// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.core.webview

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.blockingContext
import com.intellij.openapi.project.Project
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.ui.JBColor
import com.intellij.ui.jcef.JBCefBrowserBase
import com.intellij.ui.jcef.JBCefJSQuery
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.jetbrains.annotations.VisibleForTesting
import org.slf4j.event.Level
import software.aws.toolkits.core.region.AwsRegion
import software.aws.toolkits.core.utils.debug
import software.aws.toolkits.core.utils.getLogger
import software.aws.toolkits.core.utils.tryOrNull
import software.aws.toolkits.jetbrains.core.coroutines.projectCoroutineScope
import software.aws.toolkits.jetbrains.core.credentials.AwsBearerTokenConnection
import software.aws.toolkits.jetbrains.core.credentials.LastLoginIdcInfo
import software.aws.toolkits.jetbrains.core.credentials.Login
import software.aws.toolkits.jetbrains.core.credentials.ToolkitAuthManager
import software.aws.toolkits.jetbrains.core.credentials.ToolkitConnection
import software.aws.toolkits.jetbrains.core.credentials.reauthConnectionIfNeeded
import software.aws.toolkits.jetbrains.core.credentials.sso.PendingAuthorization
import software.aws.toolkits.jetbrains.core.credentials.sso.bearer.InteractiveBearerTokenProvider
import software.aws.toolkits.jetbrains.core.region.AwsRegionProvider
import software.aws.toolkits.jetbrains.utils.pollFor
import software.aws.toolkits.resources.message
import software.aws.toolkits.telemetry.AwsTelemetry
import software.aws.toolkits.telemetry.CredentialType
import software.aws.toolkits.telemetry.FeatureId
import software.aws.toolkits.telemetry.Result
import java.util.concurrent.Future
import java.util.function.Function

data class BrowserState(
    val feature: FeatureId,
    val browserCancellable: Boolean = false,
    val requireReauth: Boolean = false,
    var stage: String = "START",
    val lastLoginIdcInfo: LastLoginIdcInfo = ToolkitAuthManager.getInstance().getLastLoginIdcInfo(),
    var existingConnections: List<AwsBearerTokenConnection> = emptyList()
)

abstract class AwsLoginBrowser(
    private val project: Project,
    val domain: String,
    val webScriptUri: String
) {
    abstract val jcefBrowser: JBCefBrowserBase

    protected val jcefHandler = Function<String, JBCefJSQuery.Response> {
        val obj = LOG.tryOrNull("Unable deserialize login browser message: $it", Level.WARN) {
            objectMapper.readValue<BrowserMessage>(it)
        }

        handleBrowserMessage(obj)

        null
    }

    protected var currentAuthorization: PendingAuthorization? = null

    protected lateinit var myState: BrowserState

    protected fun updateOnPendingToken(provider: InteractiveBearerTokenProvider) {
        projectCoroutineScope(project).launch {
            val authorization = pollForAuthorization(provider)
            if (authorization != null) {
                executeJS("window.ideClient.updateAuthorization(\"${userCodeFromAuthorization(authorization)}\")")
                currentAuthorization = authorization
            }
        }
    }

    @VisibleForTesting
    internal val objectMapper = jacksonObjectMapper()

    abstract fun customize(state: BrowserState): BrowserState

    abstract fun handleBrowserMessage(message: BrowserMessage?)

    abstract fun loginBuilderId(scopes: List<String>)

    abstract fun loginIdC(url: String, region: AwsRegion, scopes: List<String>)

    abstract fun loadWebView(query: JBCefJSQuery)

    fun prepareBrowser(state: BrowserState) {
        myState = state
        customize(state)

        val jsonData = """
            {
                stage: '${state.stage}',
                regions: ${objectMapper.writeValueAsString(AwsRegionProvider.getInstance().allRegionsForService("sso").values)},
                idcInfo: {
                    profileName: '${state.lastLoginIdcInfo.profileName}',
                    startUrl: '${state.lastLoginIdcInfo.startUrl}',
                    region: '${state.lastLoginIdcInfo.region}'
                },
                cancellable: ${state.browserCancellable},
                feature: '${state.feature}',
                existConnections: ${objectMapper.writeValueAsString(state.existingConnections)}
            }
        """.trimIndent()
        executeJS("window.ideClient.prepareUi($jsonData)")
    }

    fun userCodeFromAuthorization(authorization: PendingAuthorization) = when (authorization) {
        is PendingAuthorization.DAGAuthorization -> authorization.authorization.userCode
        else -> ""
    }

    private fun executeJS(jsScript: String) {
        this.jcefBrowser.cefBrowser.let {
            it.executeJavaScript(jsScript, it.url, 0)
        }
    }

    protected fun cancelLogin() {
        // Essentially Authorization becomes a mutable that allows browser and auth to communicate canceled
        // status. There might be a risk of race condition here by changing this global, for which effort
        // has been made to avoid it (e.g. Cancel button is only enabled if Authorization has been given
        // to browser.). The worst case is that the user will see a stale user code displayed, but not
        // affecting the current login flow.
        currentAuthorization?.progressIndicator?.cancel()
        // TODO: telemetry
    }

    protected fun loginBuilderId(scopes: List<String>, loginHandler: BearerLoginHandler) {
        loginWithBackgroundContext {
            Login.BuilderId(scopes, loginHandler).login(project)
        }
    }

    protected fun loginIdC(url: String, region: AwsRegion, scopes: List<String>, loginHandler: BearerLoginHandler) {
        loginWithBackgroundContext {
            Login.IdC(url, region, scopes, loginHandler).login(project)
        }
    }

    protected fun loginIAM(profileName: String, accessKey: String, secretKey: String) {
        runInEdt {
            Login.LongLivedIAM(
                profileName,
                accessKey,
                secretKey
            ).login(project)
            AwsTelemetry.loginWithBrowser(
                project = null,
                result = Result.Succeeded,
                credentialType = CredentialType.StaticProfile
            )
        }
    }

    protected fun <T> loginWithBackgroundContext(action: () -> T): Future<T> =
        ApplicationManager.getApplication().executeOnPooledThread<T> {
            runBlocking {
                withBackgroundProgress(project, message("credentials.pending.title")) {
                    blockingContext {
                        action()
                    }
                }
            }
        }

    protected suspend fun pollForAuthorization(provider: InteractiveBearerTokenProvider): PendingAuthorization? = pollFor {
        provider.pendingAuthorization
    }

    protected fun reauth(connection: ToolkitConnection?) {
        if (connection is AwsBearerTokenConnection) {
            loginWithBackgroundContext {
                reauthConnectionIfNeeded(project, connection, ::updateOnPendingToken)
                AwsTelemetry.loginWithBrowser(
                    project = null,
                    isReAuth = true,
                    result = Result.Succeeded,
                    credentialStartUrl = connection.startUrl,
                    credentialType = CredentialType.BearerToken
                )
            }
        }
    }

    protected fun tryHandleUserCanceledLogin(e: Exception): Boolean {
        if (e !is ProcessCanceledException ||
            e.cause !is IllegalStateException ||
            e.message?.contains(message("credentials.pending.user_cancel.message")) == false
        ) {
            return false
        }
        LOG.debug(e) { "User canceled login" }
        return true
    }

    companion object {
        private val LOG = getLogger<AwsLoginBrowser>()
        fun getWebviewHTML(webScriptUri: String, query: JBCefJSQuery): String {
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
            <script type="text/javascript" src="$webScriptUri"></script>
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
    }
}
