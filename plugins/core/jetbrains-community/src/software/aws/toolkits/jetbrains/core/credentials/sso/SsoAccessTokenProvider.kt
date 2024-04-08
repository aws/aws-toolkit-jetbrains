// Copyright 2021 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.core.credentials.sso

import com.intellij.openapi.components.service
import com.intellij.openapi.progress.ProgressManager
import software.amazon.awssdk.auth.token.credentials.SdkTokenProvider
import software.amazon.awssdk.services.ssooidc.SsoOidcClient
import software.amazon.awssdk.services.ssooidc.model.AuthorizationPendingException
import software.amazon.awssdk.services.ssooidc.model.CreateTokenResponse
import software.amazon.awssdk.services.ssooidc.model.InvalidClientException
import software.amazon.awssdk.services.ssooidc.model.InvalidRequestException
import software.amazon.awssdk.services.ssooidc.model.SlowDownException
import software.aws.toolkits.core.utils.getLogger
import software.aws.toolkits.core.utils.warn
import software.aws.toolkits.jetbrains.core.credentials.sono.SONO_URL
import software.aws.toolkits.jetbrains.core.credentials.sso.pkce.ToolkitOAuthService
import software.aws.toolkits.jetbrains.utils.assertIsNonDispatchThread
import software.aws.toolkits.jetbrains.utils.sleepWithCancellation
import software.aws.toolkits.resources.message
import software.aws.toolkits.telemetry.AwsTelemetry
import software.aws.toolkits.telemetry.CredentialSourceId
import software.aws.toolkits.telemetry.Result
import java.time.Clock
import java.time.Duration
import java.time.Instant
import kotlin.reflect.jvm.jvmName

/**
 * Takes care of creating/refreshing the SSO access token required to fetch SSO-based credentials.
 */
class SsoAccessTokenProvider(
    private val ssoUrl: String,
    private val ssoRegion: String,
    private val cache: SsoCache,
    private val client: SsoOidcClient,
    private val scopes: List<String> = emptyList(),
    private val clock: Clock = Clock.systemUTC()
) : SdkTokenProvider {
    private val isPkce = true

    private val dagClientRegistrationCacheKey by lazy {
        DeviceAuthorizationClientRegistrationCacheKey(
            startUrl = ssoUrl,
            scopes = scopes,
            region = ssoRegion
        )
    }

    private val pkceClientRegistrationCacheKey by lazy {
        PKCEClientRegistrationCacheKey(
            issuerUrl = ssoUrl,
            region = ssoRegion,
            scopes = scopes
        )
    }

    private val accessTokenCacheKey by lazy {
        AccessTokenCacheKey(
            connectionId = ssoRegion,
            startUrl = ssoUrl,
            scopes = scopes
        )
    }

    override fun resolveToken() = accessToken()

    fun accessToken(): AccessToken {
        assertIsNonDispatchThread()

        loadAccessToken()?.let {
            return it
        }

        val token = if (isPkce) {
            ToolkitOAuthService.getInstance().authorize(registerPkceClient()).get()
        } else {
            pollForDAGToken()
        }

        saveAccessToken(token)

        return token
    }

    @Deprecated("Device authorization grant flow is deprecated")
    private fun registerDAGClient(): ClientRegistration {
        loadClientRegistration()?.let {
            return it
        }

        // Based on botocore: https://github.com/boto/botocore/blob/5dc8ee27415dc97cfff75b5bcfa66d410424e665/botocore/utils.py#L1753
        val registerResponse = client.registerClient {
            it.clientType(PUBLIC_CLIENT_REGISTRATION_TYPE)
            it.scopes(scopes)
            it.clientName("AWS Toolkit for JetBrains")
        }

        val registeredClient = DeviceAuthorizationClientRegistration(
            registerResponse.clientId(),
            registerResponse.clientSecret(),
            Instant.ofEpochSecond(registerResponse.clientSecretExpiresAt()),
            scopes
        )

        saveClientRegistration(registeredClient)

        return registeredClient
    }

    private fun registerPkceClient(): PKCEClientRegistration {
        loadClientRegistration()?.let {
            return it as PKCEClientRegistration
        }

        val grantTypes = listOf("authorization_code", "refresh_token")
        val redirectUris = listOf("http://127.0.0.1/oauth/callback")

        if (!ssoUrl.contains("identitycenter")) {
            getLogger<SsoAccessTokenProvider>().warn { "$ssoUrl does not appear to be a valid issuer URL" }
        }

        val registerResponse = client.registerClient {
            // TODO change this
            it.clientName("AWS Toolkit for JetBrains")
            it.clientType(PUBLIC_CLIENT_REGISTRATION_TYPE)
            it.scopes(scopes)
            it.grantTypes(grantTypes)
            it.redirectUris(redirectUris)
            it.issuerUrl(ssoUrl)
        }

        val registeredClient = PKCEClientRegistration(
            registerResponse.clientId(),
            registerResponse.clientSecret(),
            Instant.ofEpochSecond(registerResponse.clientSecretExpiresAt()),
            scopes,
            issuerUrl = ssoUrl,
            region = ssoRegion,
            clientType = PUBLIC_CLIENT_REGISTRATION_TYPE,
            grantTypes = grantTypes,
            redirectUris = redirectUris
        )

        saveClientRegistration(registeredClient)

        return registeredClient
    }

    @Deprecated("Device authorization grant flow is deprecated")
    private fun authorizeDAGClient(clientId: ClientRegistration): Authorization {
        // Should not be cached, only good for 1 token and short lived
        val authorizationResponse = try {
            client.startDeviceAuthorization {
                it.startUrl(ssoUrl)
                it.clientId(clientId.clientId)
                it.clientSecret(clientId.clientSecret)
            }
        } catch (e: InvalidClientException) {
            invalidateClientRegistration()
            throw e
        }

        val createTime = Instant.now(clock)

        return Authorization(
            authorizationResponse.deviceCode(),
            authorizationResponse.userCode(),
            authorizationResponse.verificationUri(),
            authorizationResponse.verificationUriComplete(),
            createTime.plusSeconds(authorizationResponse.expiresIn().toLong()),
            authorizationResponse.interval()?.toLong()
                ?: DEFAULT_INTERVAL_SECS,
            createTime
        )
    }

    @Deprecated("Device authorization grant flow is deprecated")
    private fun pollForDAGToken(): AccessToken {
        val onPendingToken = service<SsoLoginCallbackProvider>().getProvider(ssoUrl)
        val progressIndicator = ProgressManager.getInstance().progressIndicator
        val registration = registerDAGClient()
        val authorization = authorizeDAGClient(registration)

        progressIndicator?.text2 = message("aws.sso.signing.device.waiting", authorization.userCode)
        onPendingToken.tokenPending(authorization)

        var backOffTime = Duration.ofSeconds(authorization.pollInterval)

        while (true) {
            try {
                val tokenResponse = client.createToken {
                    it.clientId(registration.clientId)
                    it.clientSecret(registration.clientSecret)
                    it.grantType(DEVICE_GRANT_TYPE)
                    it.deviceCode(authorization.deviceCode)
                }

                onPendingToken.tokenRetrieved()

                return tokenResponse.toDAGAccessToken(authorization.createdAt)
            } catch (e: SlowDownException) {
                backOffTime = backOffTime.plusSeconds(SLOW_DOWN_DELAY_SECS)
            } catch (e: AuthorizationPendingException) {
                // Do nothing, keep polling
            } catch (e: Exception) {
                onPendingToken.tokenRetrievalFailure(e)
                throw e
            }

            sleepWithCancellation(backOffTime, progressIndicator)
        }
    }

    fun refreshToken(currentToken: AccessToken): AccessToken {
        if (currentToken.refreshToken == null) {
            val tokenCreationTime = currentToken.createdAt

            if (tokenCreationTime != Instant.EPOCH) {
                val sessionDuration = Duration.between(Instant.now(clock), tokenCreationTime)
                val credentialSourceId = if (currentToken.ssoUrl == SONO_URL) CredentialSourceId.AwsId else CredentialSourceId.IamIdentityCenter
                AwsTelemetry.refreshCredentials(
                    project = null,
                    result = Result.Failed,
                    sessionDuration = sessionDuration.toHours().toInt(),
                    credentialSourceId = credentialSourceId,
                    reason = "Null refresh token"
                )
            }

            throw InvalidRequestException.builder().message("Requested token refresh, but refresh token was null").build()
        }

        val registration = loadClientRegistration() ?: throw InvalidClientException.builder().message("Unable to load client registration").build()

        val newToken = client.createToken {
            it.clientId(registration.clientId)
            it.clientSecret(registration.clientSecret)
            it.grantType(REFRESH_GRANT_TYPE)
            it.refreshToken(currentToken.refreshToken)
        }

        val token = newToken.toDAGAccessToken(currentToken.createdAt)
        saveAccessToken(token)

        return token
    }

    fun invalidate() {
        if (scopes.isEmpty()) {
            cache.invalidateAccessToken(ssoUrl)
        } else {
            cache.invalidateAccessToken(accessTokenCacheKey)
        }
    }

    private fun loadClientRegistration(): ClientRegistration? = if (scopes.isEmpty()) {
        cache.loadClientRegistration(ssoRegion)?.let {
            return it
        }
    } else {
        if (isPkce) {
            cache.loadClientRegistration(pkceClientRegistrationCacheKey)?.let {
                return it
            }
        } else {
            cache.loadClientRegistration(dagClientRegistrationCacheKey)?.let {
                return it
            }
        }
    }

    private fun saveClientRegistration(registration: ClientRegistration) {
        if (scopes.isEmpty()) {
            cache.saveClientRegistration(ssoRegion, registration)
        } else {
            if (isPkce) {
                cache.saveClientRegistration(pkceClientRegistrationCacheKey, registration)

            } else {
                cache.saveClientRegistration(dagClientRegistrationCacheKey, registration)
            }
        }
    }

    private fun invalidateClientRegistration() {
        if (scopes.isEmpty()) {
            cache.invalidateClientRegistration(ssoRegion)
        } else {
            cache.invalidateClientRegistration(dagClientRegistrationCacheKey)
            cache.invalidateClientRegistration(pkceClientRegistrationCacheKey)
        }
    }

    internal fun loadAccessToken(): AccessToken? = if (scopes.isEmpty()) {
        cache.loadAccessToken(ssoUrl)?.let {
            return it
        }
    } else {
        cache.loadAccessToken(accessTokenCacheKey)?.let {
            if (isPkce) {
                check(it is PKCEAuthorizationGrantToken) {
                    "SsoAccessTokenProvider expected instance of PKCEAuthorizationGrantToken token from disk cache, but found: ${it::class.jvmName}"
                }
            }
            return it
        }
    }

    private fun saveAccessToken(token: AccessToken) {
        if (scopes.isEmpty()) {
            cache.saveAccessToken(ssoUrl, token)
        } else {
            cache.saveAccessToken(accessTokenCacheKey, token)
        }
    }

    private fun CreateTokenResponse.toDAGAccessToken(creationTime: Instant): AccessToken {
        val expirationTime = Instant.now(clock).plusSeconds(expiresIn().toLong())

        return DeviceAuthorizationGrantToken(
            startUrl = ssoUrl,
            region = ssoRegion,
            accessToken = accessToken(),
            refreshToken = refreshToken(),
            expiresAt = expirationTime,
            createdAt = creationTime
        )
    }

    private companion object {
        const val PUBLIC_CLIENT_REGISTRATION_TYPE = "public"
        const val DEVICE_GRANT_TYPE = "urn:ietf:params:oauth:grant-type:device_code"
        const val REFRESH_GRANT_TYPE = "refresh_token"

        // Default number of seconds to poll for token, https://tools.ietf.org/html/draft-ietf-oauth-device-flow-15#section-3.5
        const val DEFAULT_INTERVAL_SECS = 5L
        const val SLOW_DOWN_DELAY_SECS = 5L
    }
}
