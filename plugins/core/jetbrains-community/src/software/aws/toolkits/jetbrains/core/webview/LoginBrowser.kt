// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.core.webview

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
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
import software.aws.toolkits.core.utils.error
import software.aws.toolkits.core.utils.getLogger
import software.aws.toolkits.core.utils.tryOrNull
import software.aws.toolkits.jetbrains.core.coroutines.projectCoroutineScope
import software.aws.toolkits.jetbrains.core.credentials.AuthProfile
import software.aws.toolkits.jetbrains.core.credentials.AwsBearerTokenConnection
import software.aws.toolkits.jetbrains.core.credentials.Login
import software.aws.toolkits.jetbrains.core.credentials.ToolkitConnection
import software.aws.toolkits.jetbrains.core.credentials.ToolkitConnectionManager
import software.aws.toolkits.jetbrains.core.credentials.pinning.CodeCatalystConnection
import software.aws.toolkits.jetbrains.core.credentials.pinning.QConnection
import software.aws.toolkits.jetbrains.core.credentials.reauthConnectionIfNeeded
import software.aws.toolkits.jetbrains.core.credentials.sono.CODECATALYST_SCOPES
import software.aws.toolkits.jetbrains.core.credentials.sono.Q_SCOPES
import software.aws.toolkits.jetbrains.core.credentials.sono.SONO_URL
import software.aws.toolkits.jetbrains.core.credentials.sso.PendingAuthorization
import software.aws.toolkits.jetbrains.core.credentials.sso.bearer.InteractiveBearerTokenProvider
import software.aws.toolkits.jetbrains.core.credentials.ssoErrorMessageFromException
import software.aws.toolkits.jetbrains.utils.pluginAwareExecuteOnPooledThread
import software.aws.toolkits.jetbrains.utils.pollFor
import software.aws.toolkits.resources.message
import software.aws.toolkits.telemetry.AuthTelemetry
import software.aws.toolkits.telemetry.AwsTelemetry
import software.aws.toolkits.telemetry.CredentialSourceId
import software.aws.toolkits.telemetry.CredentialType
import software.aws.toolkits.telemetry.FeatureId
import software.aws.toolkits.telemetry.Result
import java.time.Duration
import java.util.Timer
import java.util.TimerTask
import java.util.concurrent.Future
import java.util.function.Function

data class BrowserState(val feature: FeatureId, val browserCancellable: Boolean = false, val requireReauth: Boolean = false)

abstract class LoginBrowser(
    private val project: Project,
    val domain: String,
    val webScriptUri: String
) {
    abstract val jcefBrowser: JBCefBrowserBase

    protected val jcefHandler = Function<String, JBCefJSQuery.Response> {
        val obj = LOG.tryOrNull("${this::class.simpleName} unable deserialize login browser message: $it", Level.ERROR) {
            objectMapper.readValue<BrowserMessage>(it)
        }

        handleBrowserMessage(obj)

        null
    }
    protected var currentAuthorization: PendingAuthorization? = null

    @VisibleForTesting
    internal val objectMapper = jacksonObjectMapper()

    abstract fun handleBrowserMessage(message: BrowserMessage?)

    protected data class BearerConnectionSelectionSettings(val currentSelection: AwsBearerTokenConnection, val onChange: (AwsBearerTokenConnection) -> Unit)

    protected val selectionSettings = mutableMapOf<String, BearerConnectionSelectionSettings>()

    private var browserOpenTimer: Timer? = null

    private fun startBrowserOpenTimer(credentialSourceId: CredentialSourceId) {
        browserOpenTimer = Timer()
        browserOpenTimer?.schedule(
            object : TimerTask() {
                override fun run() {
                    AwsTelemetry.loginWithBrowser(
                        project = null,
                        credentialStartUrl = SONO_URL,
                        result = Result.Failed,
                        reason = "Browser authentication idle for more than 15min",
                        credentialSourceId = credentialSourceId
                    )
                    AuthTelemetry.addConnection(
                        result = Result.Failed,
                        reason = "Browser authentication idle for more than 15min",
                        credentialSourceId = credentialSourceId
                    )
                    stopAndClearBrowserOpenTimer()
                }
            },
            Duration.ofMinutes(15).toMillis(),
        )
    }

    protected fun stopAndClearBrowserOpenTimer() {
        if (browserOpenTimer != null) {
            browserOpenTimer?.cancel()
            browserOpenTimer?.purge()
            browserOpenTimer = null
        }
    }

    protected val onPendingToken: (InteractiveBearerTokenProvider) -> Unit = { provider ->
        startBrowserOpenTimer(if (provider.startUrl == SONO_URL) CredentialSourceId.AwsId else CredentialSourceId.IamIdentityCenter)
        projectCoroutineScope(project).launch {
            val authorization = pollForAuthorization(provider)
            if (authorization != null) {
                executeJS("window.ideClient.updateAuthorization(\"${userCodeFromAuthorization(authorization)}\")")
                currentAuthorization = authorization
            }
        }
    }

    abstract fun prepareBrowser(state: BrowserState)

    fun executeJS(jsScript: String) {
        this.jcefBrowser.cefBrowser.let {
            it.executeJavaScript(jsScript, it.url, 0)
        }
    }

    // TODO: Dumb and will be addressed in followup PRs
    protected fun writeValueAsString(o: Any) = objectMapper.writeValueAsString(o)

    protected fun cancelLogin() {
        stopAndClearBrowserOpenTimer()
        // Essentially Authorization becomes a mutable that allows browser and auth to communicate canceled
        // status. There might be a risk of race condition here by changing this global, for which effort
        // has been made to avoid it (e.g. Cancel button is only enabled if Authorization has been given
        // to browser.). The worst case is that the user will see a stale user code displayed, but not
        // affecting the current login flow.
        currentAuthorization?.progressIndicator?.cancel()
        // TODO: telemetry
    }

    fun userCodeFromAuthorization(authorization: PendingAuthorization) = when (authorization) {
        is PendingAuthorization.DAGAuthorization -> authorization.authorization.userCode
        else -> ""
    }

    private fun isReAuth(scopes: List<String>): Boolean = ToolkitConnectionManager.getInstance(project)
        .let {
            if (scopes.toSet().intersect(Q_SCOPES.toSet()).isNotEmpty()) {
                it.activeConnectionForFeature(QConnection.getInstance()) != null
            } else if (scopes.toSet().intersect(CODECATALYST_SCOPES.toSet()).isNotEmpty()) {
                it.activeConnectionForFeature(CodeCatalystConnection.getInstance()) != null
            } else {
                val activeCon = it.activeConnection()
                val ccCon = it.activeConnectionForFeature(CodeCatalystConnection.getInstance())
                val qCon = it.activeConnectionForFeature(QConnection.getInstance())
                activeCon != null && activeCon != ccCon && activeCon != qCon
            }
        }

    open fun loginBuilderId(scopes: List<String>) {
        val onError: (Exception) -> Unit = { e ->
            stopAndClearBrowserOpenTimer()
            tryHandleUserCanceledLogin(e)
            AwsTelemetry.loginWithBrowser(
                project = null,
                credentialStartUrl = SONO_URL,
                result = Result.Failed,
                reason = e.message,
                credentialSourceId = CredentialSourceId.AwsId
            )
            AuthTelemetry.addConnection(
                result = Result.Failed,
                credentialSourceId = CredentialSourceId.AwsId,
                reason = e.message
            )
        }
        val onSuccess: () -> Unit = {
            stopAndClearBrowserOpenTimer()
            AwsTelemetry.loginWithBrowser(
                project = null,
                credentialStartUrl = SONO_URL,
                result = Result.Succeeded,
                credentialSourceId = CredentialSourceId.AwsId
            )
            AuthTelemetry.addConnection(
                result = Result.Succeeded,
                credentialSourceId = CredentialSourceId.AwsId
            )
        }

        loginWithBackgroundContext {
            Login
                .BuilderId(scopes, onPendingToken, onError, onSuccess)
                .loginBuilderId(project)
        }
    }

    open fun loginIdC(url: String, region: AwsRegion, scopes: List<String>) {
        // assumes scopes contains either Q or non-Q permissions but not both
        val isReAuth = isReAuth(scopes)

        val onError: (Exception, AuthProfile) -> Unit = { e, profile ->
            stopAndClearBrowserOpenTimer()
            val message = ssoErrorMessageFromException(e)
            if (!tryHandleUserCanceledLogin(e)) {
                LOG.error(e) { "Failed to authenticate: message: $message; profile: $profile" }
            }
            AwsTelemetry.loginWithBrowser(
                project = null,
                credentialStartUrl = url,
                isReAuth = isReAuth,
                result = Result.Failed,
                reason = message,
                credentialSourceId = CredentialSourceId.IamIdentityCenter
            )
            AuthTelemetry.addConnection(
                result = Result.Failed,
                credentialSourceId = CredentialSourceId.IamIdentityCenter,
                reason = message,
                isReAuth = isReAuth,
            )
        }
        val onSuccess: () -> Unit = {
            stopAndClearBrowserOpenTimer()
            AwsTelemetry.loginWithBrowser(
                project = null,
                result = Result.Succeeded,
                isReAuth = isReAuth,
                credentialType = CredentialType.BearerToken,
                credentialStartUrl = url,
                credentialSourceId = CredentialSourceId.IamIdentityCenter
            )
            AuthTelemetry.addConnection(
                project = null,
                result = Result.Succeeded,
                isReAuth = isReAuth,
                credentialSourceId = CredentialSourceId.IamIdentityCenter
            )
        }
        loginWithBackgroundContext {
            Login
                .IdC(url, region, scopes, onPendingToken, onSuccess, onError)
                .loginIdc(project)
        }
    }

    open fun loginIAM(profileName: String, accessKey: String, secretKey: String) {
        runInEdt {
            Login.LongLivedIAM(
                profileName,
                accessKey,
                secretKey
            ).loginIAM(
                project,
                { error ->
                    AwsTelemetry.loginWithBrowser(
                        project = null,
                        result = Result.Failed,
                        reason = error.message,
                        credentialType = CredentialType.StaticProfile
                    )
                },
                {
                    AwsTelemetry.loginWithBrowser(
                        project = null,
                        result = Result.Failed,
                        reason = "Profile already exists",
                        credentialType = CredentialType.StaticProfile
                    )
                },
                {
                    AwsTelemetry.loginWithBrowser(
                        project = null,
                        result = Result.Failed,
                        reason = "Connection validation error",
                        credentialType = CredentialType.StaticProfile
                    )
                }
            )
            AwsTelemetry.loginWithBrowser(
                project = null,
                result = Result.Succeeded,
                credentialType = CredentialType.StaticProfile
            )
        }
    }

    protected fun <T> loginWithBackgroundContext(action: () -> T): Future<T> =
        pluginAwareExecuteOnPooledThread {
            runBlocking {
                withBackgroundProgress(project, message("credentials.pending.title")) {
                    blockingContext {
                        action()
                    }
                }
            }
        }

    abstract fun loadWebView(query: JBCefJSQuery)

    protected suspend fun pollForAuthorization(provider: InteractiveBearerTokenProvider): PendingAuthorization? = pollFor {
        provider.pendingAuthorization
    }

    protected fun reauth(connection: ToolkitConnection?) {
        if (connection is AwsBearerTokenConnection) {
            loginWithBackgroundContext {
                reauthConnectionIfNeeded(project, connection, onPendingToken)
            }
            stopAndClearBrowserOpenTimer()
        }
    }

    private fun tryHandleUserCanceledLogin(e: Exception): Boolean {
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
        private val LOG = getLogger<LoginBrowser>()
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
