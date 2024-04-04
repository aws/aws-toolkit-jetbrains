// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.core.credentials.sso.pkce

import com.intellij.collaboration.auth.OAuthCallbackHandlerBase
import com.intellij.collaboration.auth.credentials.Credentials
import com.intellij.collaboration.auth.services.OAuthCredentialsAcquirer
import com.intellij.collaboration.auth.services.OAuthRequest
import com.intellij.collaboration.auth.services.OAuthService
import com.intellij.collaboration.auth.services.OAuthServiceBase
import com.intellij.collaboration.auth.services.PkceUtils
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.util.Url
import com.intellij.util.Urls.newFromEncoded
import com.intellij.util.io.DigestUtil
import io.netty.handler.codec.http.FullHttpRequest
import org.jetbrains.ide.BuiltInServerManager
import software.amazon.awssdk.services.ssooidc.model.InvalidGrantException
import software.aws.toolkits.core.utils.error
import software.aws.toolkits.core.utils.getLogger
import software.aws.toolkits.jetbrains.core.credentials.sso.AccessToken
import software.aws.toolkits.jetbrains.core.credentials.sso.bearer.buildUnmanagedSsoOidcClient
import java.math.BigInteger
import java.time.Instant
import java.util.Base64
import java.util.concurrent.CompletableFuture

@Service
class ToolkitOAuthService : OAuthServiceBase<Credentials>() {
    override val name: String = "aws/toolkit"

    fun hasPendingRequest() = currentRequest.get() != null

    fun authorize() {
        try {
            println(authorize(ToolkitOAuthRequest()).get())
        } catch (e: Exception) {
            val cause = e.cause
            if (cause is InvalidGrantException) {
                getLogger<ToolkitOAuthService>().error(e) { cause.errorDescription() }
            } else {
                throw e
            }
        }
    }

    override fun authorize(request: OAuthRequest<Credentials>): CompletableFuture<Credentials> {
        return super.authorize(request)
    }

    override fun handleOAuthServerCallback(path: String, parameters: Map<String, List<String>>): OAuthService.OAuthResult<Credentials>? {
        val request = currentRequest.get() ?: return null
        val toolkitRequest = request.request as? ToolkitOAuthRequest ?: return null

        val callbackState = parameters["state"]?.firstOrNull()
        if (toolkitRequest.csrfToken != callbackState) {
            request.result.completeExceptionally(RuntimeException("Invalid CSRF token"))
            return OAuthService.OAuthResult(request.request, false)
        }

        return super.handleOAuthServerCallback(path, parameters)
    }

    override fun revokeToken(token: String) {
        TODO("Not yet implemented")
    }

    companion object {
        fun getInstance() = service<ToolkitOAuthService>()
    }
}

private class ToolkitOAuthRequest : OAuthRequest<Credentials> {
    private val port: Int get() = BuiltInServerManager.getInstance().port
    private val base64Encoder = Base64.getUrlEncoder().withoutPadding()

    // 160 bits of entropy, per https://datatracker.ietf.org/doc/html/rfc6749#section-10.10
    internal val csrfToken = randB64url(160)

    // 256 bits of entropy, per https://datatracker.ietf.org/doc/html/rfc7636#section-7.1
    private val codeVerifier = randB64url(256)

    private val codeChallenge = PkceUtils.generateShaCodeChallenge(codeVerifier, base64Encoder)

    override val authorizationCodeUrl: Url
        get() = newFromEncoded("http://127.0.0.1:$port/oauth/callback")

    val redirectUri
        get() = authorizationCodeUrl.toExternalForm()

    override val credentialsAcquirer: OAuthCredentialsAcquirer<Credentials> = ToolkitOauthCredentialsAcquirer(codeVerifier, redirectUri)

    override val authUrlWithParameters: Url
        get() {
            return newFromEncoded("https://oidc.us-east-1.amazonaws.com/authorize").addParameters(
                mapOf(
                    "response_type" to "code",
                    "client_id" to registration.clientId(),
                    "redirect_uri" to redirectUri,
                    "scopes" to "sso:account:access codewhisperer:completions",
                    "state" to csrfToken,
                    "code_challenge" to codeChallenge,
                    "code_challenge_method" to "S256"
                )
            )
        }

    private fun randB64url(bits: Int): String = base64Encoder.encodeToString(BigInteger(bits, DigestUtil.random).toByteArray())
}

// exchange for real token
internal class ToolkitOauthCredentialsAcquirer(
    private val codeVerifier: String,
    private val redirectUri: String,
) : OAuthCredentialsAcquirer<Credentials> {
    override fun acquireCredentials(code: String): OAuthCredentialsAcquirer.AcquireCredentialsResult<Credentials> {
        val token = buildUnmanagedSsoOidcClient("us-east-1").use { client ->
            client.createToken {
                it.clientId(registration.clientId())
                it.clientSecret(registration.clientSecret())
                it.grantType("authorization_code")
                it.redirectUri(redirectUri)
                it.codeVerifier(codeVerifier)
                it.code(code)
            }
        }

        return OAuthCredentialsAcquirer.AcquireCredentialsResult.Success(
            AccessToken(
                startUrl = "",
                region = "us-east-1",
                accessToken = token.accessToken(),
                refreshToken = token.refreshToken(),
                expiresAt = Instant.now().plusSeconds(token.expiresIn().toLong()),
                createdAt = Instant.now()
            )
        )
    }
}

internal class ToolkitOAuthCallbackHandler : OAuthCallbackHandlerBase() {
    override fun oauthService() = ToolkitOAuthService.getInstance()

    // on success / fail
    override fun handleOAuthResult(oAuthResult: OAuthService.OAuthResult<*>): AcceptCodeHandleResult {
        // focus should be on requesting component?
        runInEdt {
            IdeFocusManager.getGlobalInstance().getLastFocusedIdeWindow()?.toFront()
        }

        // provide a better page
        return AcceptCodeHandleResult.Page(if (oAuthResult.isAccepted) "complete" else "error")
    }

    override fun isSupported(request: FullHttpRequest): Boolean {
        // only handle if we're actively waiting on a redirect
        if (!oauthService().hasPendingRequest()) {
            return false
        }

        // only handle the /oauth/callback endpoint
        return request.uri().trim('/').startsWith("oauth/callback")
    }
}

internal fun registration() = buildUnmanagedSsoOidcClient("us-east-1").use { client ->
    client.registerClient {
        it.clientName("MyAwesomeClient")
        it.clientType("public")
        it.scopes("sso:account:access", "codewhisperer:completions")
        it.grantTypes("authorization_code", "refresh_token")
        it.redirectUris("http://127.0.0.1/oauth/callback")
        it.issuerUrl("https://identitycenter.amazonaws.com/ssoins-")
    }
}

internal val registration by lazy { registration() }

class PKCE : DumbAwareAction("PKCE") {
    override fun actionPerformed(e: AnActionEvent) {
        ApplicationManager.getApplication().executeOnPooledThread {
            ToolkitOAuthService.getInstance().authorize()
        }
    }
}
