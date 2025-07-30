// PreregisteredOidcAccessTokenProvider.kt
package software.aws.toolkits.jetbrains.core.credentials.sso

import software.amazon.awssdk.auth.token.credentials.SdkTokenProvider
import software.aws.toolkits.jetbrains.core.credentials.sso.pkce.ToolkitOAuthService
import software.aws.toolkits.jetbrains.utils.assertIsNonDispatchThread
import java.time.Instant
import java.util.concurrent.atomic.AtomicReference

/**
 * OIDC provider for preregistered clients that extends SsoAccessTokenCacheAccessor
 */
class ExternalOidcAccessTokenProvider(
    override val ssoUrl: String,
    override val ssoRegion: String = "global",
    override val cache: SsoCache,
    override val scopes: List<String>,
    private val preregisteredClientId: String,
    private val preregisteredClientSecret: String = "",
) : SsoAccessTokenCacheAccessor(), SdkTokenProvider, InteractiveAccessTokenProvider {

    private val _authorization = AtomicReference<PendingAuthorization?>()
    override val authorization: PendingAuthorization? get() = _authorization.get()

    override fun resolveToken() = accessToken()

    override fun accessToken(): AccessToken {
        assertIsNonDispatchThread()

        loadAccessToken()?.let { return it }

        val registration = PKCEClientRegistration(
            clientId = preregisteredClientId,
            clientSecret = preregisteredClientSecret,
            expiresAt = Instant.MAX,
            scopes = scopes,
            issuerUrl = ssoUrl,
            region = ssoRegion,
            clientType = PUBLIC_CLIENT_REGISTRATION_TYPE,
            grantTypes = PKCE_GRANT_TYPES,
            redirectUris = PKCE_REDIRECT_URIS
        )

        val future = ToolkitOAuthService.getInstance().authorize(registration)
        val progressIndicator = com.intellij.openapi.progress.ProgressManager.getInstance().progressIndicator
            ?: com.intellij.openapi.progress.EmptyProgressIndicator()

        _authorization.set(PendingAuthorization.PKCEAuthorization(future, progressIndicator))

        while (true) {
            if (future.isDone) {
                _authorization.set(null)
                val token = future.get()
                saveAccessToken(token)
                return token
            }

            try {
                software.aws.toolkits.jetbrains.utils.sleepWithCancellation(
                    java.time.Duration.ofMillis(100), progressIndicator
                )
            } catch (e: com.intellij.openapi.progress.ProcessCanceledException) {
                future.cancel(true)
                _authorization.set(null)
                throw e
            }
        }
    }

    private fun saveAccessToken(token: AccessToken) {
        when (token) {
            is PKCEAuthorizationGrantToken -> cache.saveAccessToken(pkceAccessTokenCacheKey, token)
            else -> throw IllegalArgumentException("Unsupported token type: ${token::class}")
        }
    }

    override fun refreshToken(currentToken: AccessToken): AccessToken {
        if (currentToken.refreshToken == null) {
            throw software.amazon.awssdk.services.ssooidc.model.InvalidRequestException.builder()
                .message("Requested token refresh, but refresh token was null").build()
        }

        val token = software.aws.toolkits.jetbrains.core.credentials.sso.bearer.buildUnmanagedSsoOidcClient(ssoRegion).use { client ->
            client.createToken {
                it.clientId(preregisteredClientId)
                it.clientSecret(preregisteredClientSecret)
                it.grantType(REFRESH_GRANT_TYPE)
                it.refreshToken(currentToken.refreshToken)
            }
        }

        val newToken = PKCEAuthorizationGrantToken(
            issuerUrl = ssoUrl,
            region = ssoRegion,
            accessToken = token.accessToken(),
            refreshToken = token.refreshToken(),
            expiresAt = java.time.Instant.now().plusSeconds(token.expiresIn().toLong()),
            createdAt = currentToken.createdAt
        )

        saveAccessToken(newToken)
        return newToken
    }
}
