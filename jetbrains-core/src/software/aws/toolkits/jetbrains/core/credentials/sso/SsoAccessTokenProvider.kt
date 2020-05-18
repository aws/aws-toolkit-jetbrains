// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.core.credentials.sso

import software.amazon.awssdk.services.ssooidc.SsoOidcClient
import software.amazon.awssdk.services.ssooidc.model.AuthorizationPendingException
import software.amazon.awssdk.services.ssooidc.model.SlowDownException
import java.time.Clock
import java.time.Duration
import java.time.Instant

/**
 * Internal class that takes care of creating/refreshing the SSO access token required to fetch SSO-based credentials.
 */
class SsoAccessTokenProvider(
    private val ssoUrl: String,
    private val ssoRegion: String,
    private val onPendingToken: SsoLoginCallback,
    private val ssoOidcClient: SsoOidcClient,
    private val cache: DiskCache = DiskCache(),
    private val clock: Clock = Clock.systemUTC()
) {
    internal fun accessToken(): AccessToken {
        cache.loadAccessToken(ssoUrl)?.let {
            return it
        }

        val token = pollForToken()

        cache.saveAccessToken(ssoUrl, token)

        return token
    }

    private fun registerClient(): ClientRegistration {
        cache.loadClientRegistration(ssoRegion)?.let {
            return it
        }

        // Based on botocore: https://github.com/boto/botocore/blob/v2/botocore/utils.py#L1782
        val registerResponse = ssoOidcClient.registerClient {
            it.clientType(CLIENT_REGISTRATION_TYPE)
            it.clientName("aws-toolkit-jetbrains-${Instant.now(clock)}")
        }

        val registeredClient = ClientRegistration(
            registerResponse.clientId(),
            registerResponse.clientSecret(),
            Instant.ofEpochSecond(registerResponse.clientSecretExpiresAt())
        )

        cache.saveClientRegistration(ssoRegion, registeredClient)

        return registeredClient
    }

    private fun authorizeClient(clientId: ClientRegistration): Authorization {
        // Should not be cached, only good for 1 token and short lived
        val authorizationResponse = ssoOidcClient.startDeviceAuthorization {
            it.startUrl(ssoUrl)
            it.clientId(clientId.clientId)
            it.clientSecret(clientId.clientSecret)
        }

        return Authorization(
            authorizationResponse.deviceCode(),
            authorizationResponse.userCode(),
            authorizationResponse.verificationUri(),
            authorizationResponse.verificationUriComplete(),
            Instant.now(clock).plusSeconds(authorizationResponse.expiresIn().toLong()),
            authorizationResponse.interval()?.toLong() ?: DEFAULT_INTERVAL
        )
    }

    private fun pollForToken(): AccessToken {
        val registration = registerClient()
        val authorization = authorizeClient(registration)

        onPendingToken.tokenPending(authorization)

        var backOffTime = Duration.ofSeconds(authorization.pollIntervalSeconds)

        while (true) {
            try {
                val tokenResponse = ssoOidcClient.createToken {
                    it.clientId(registration.clientId)
                    it.clientSecret(registration.clientSecret)
                    it.grantType(GRANT_TYPE)
                    it.deviceCode(authorization.deviceCode)
                }

                val expirationTime = Instant.now(clock).plusSeconds(tokenResponse.expiresIn().toLong())

                onPendingToken.tokenRetrieved()

                return AccessToken(
                    ssoUrl,
                    ssoRegion,
                    tokenResponse.accessToken(),
                    expirationTime
                )
            } catch (e: SlowDownException) {
                backOffTime = backOffTime.plusSeconds(SLOW_DOWN_DELAY)
            } catch (e: AuthorizationPendingException) {
                // Do nothing, keep polling
            } catch (e: Exception) {
                onPendingToken.tokenRetrievalFailure(e)
                throw e
            }

            Thread.sleep(backOffTime.toMillis())
        }
    }

    private companion object {
        const val CLIENT_REGISTRATION_TYPE = "public"
        const val GRANT_TYPE = "urn:ietf:params:oauth:grant-type:device_code"
        // Default number of seconds to poll for token, https://tools.ietf.org/html/draft-ietf-oauth-device-flow-15#section-3.5
        const val DEFAULT_INTERVAL = 5L
        const val SLOW_DOWN_DELAY = 5L
    }
}
