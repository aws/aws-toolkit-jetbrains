// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.core.credentials.sso

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.stub
import com.nhaarman.mockitokotlin2.verify
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.Before
import org.junit.Test
import software.amazon.awssdk.services.ssooidc.SsoOidcClient
import software.amazon.awssdk.services.ssooidc.model.AuthorizationPendingException
import software.amazon.awssdk.services.ssooidc.model.CreateTokenRequest
import software.amazon.awssdk.services.ssooidc.model.CreateTokenResponse
import software.amazon.awssdk.services.ssooidc.model.InvalidRequestException
import software.amazon.awssdk.services.ssooidc.model.RegisterClientRequest
import software.amazon.awssdk.services.ssooidc.model.RegisterClientResponse
import software.amazon.awssdk.services.ssooidc.model.SlowDownException
import software.amazon.awssdk.services.ssooidc.model.SsoOidcException
import software.amazon.awssdk.services.ssooidc.model.StartDeviceAuthorizationRequest
import software.amazon.awssdk.services.ssooidc.model.StartDeviceAuthorizationResponse
import software.aws.toolkits.jetbrains.utils.delegateMock
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

class SsoAccessTokenProviderTest {
    private val now = Instant.now().truncatedTo(ChronoUnit.MILLIS)
    private val clock = Clock.fixed(now, ZoneOffset.UTC)

    private val ssoUrl = "https://123456.awsapps.com/start"
    private val ssoRegion = "us-west-2"
    private val clientId = "client123"
    private val clientSecret = "clientSecret123"

    private lateinit var notifyTokenPending: NotifyTokenPending
    private lateinit var ssoOidcClient: SsoOidcClient
    private lateinit var accessTokenProvider: SsoAccessTokenProvider
    private lateinit var diskCache: DiskCache

    @Before
    fun setUp() {
        ssoOidcClient = delegateMock()
        notifyTokenPending = mock()
        diskCache = mock()

        accessTokenProvider = SsoAccessTokenProvider(ssoUrl, ssoRegion, notifyTokenPending, ssoOidcClient, diskCache, clock)
    }

    @Test
    fun getAccessTokenWithAccessTokenCache() {
        val accessToken = AccessToken(ssoUrl, ssoRegion, "dummyToken", clock.instant())
        diskCache.stub {
            on(
                diskCache.loadAccessToken(ssoUrl)
            ).thenReturn(accessToken)
        }

        assertThat(accessTokenProvider.accessToken()).usingRecursiveComparison()
            .isEqualTo(accessToken)
    }

    @Test
    fun getAccessTokenWithClientRegistrationCache() {
        val expirationClientRegistration = clock.instant().plusSeconds(120)
        diskCache.stub {
            on(
                diskCache.loadClientRegistration(ssoRegion)
            ).thenReturn(
                ClientRegistration(clientId, clientSecret, expirationClientRegistration)
            )
        }

        ssoOidcClient.stub {
            on(
                ssoOidcClient.startDeviceAuthorization(
                    StartDeviceAuthorizationRequest.builder()
                        .clientId(clientId)
                        .clientSecret(clientSecret)
                        .startUrl(ssoUrl)
                        .build()
                )
            ).thenReturn(
                StartDeviceAuthorizationResponse.builder()
                    .expiresIn(120)
                    .deviceCode("dummyCode")
                    .userCode("dummyUserCode")
                    .verificationUri("someUrl")
                    .verificationUriComplete("someUrlComplete")
                    .build()
            )

            on(
                ssoOidcClient.createToken(
                    CreateTokenRequest.builder()
                        .clientId(clientId)
                        .clientSecret(clientSecret)
                        .deviceCode("dummyCode")
                        .grantType("urn:ietf:params:oauth:grant-type:device_code")
                        .build()
                )
            ).thenReturn(
                CreateTokenResponse.builder()
                    .accessToken("accessToken")
                    .expiresIn(180)
                    .build()
            )
        }

        val accessToken = accessTokenProvider.accessToken()
        assertThat(accessToken).usingRecursiveComparison()
            .isEqualTo(
                AccessToken(
                    ssoUrl,
                    ssoRegion,
                    "accessToken",
                    clock.instant().plusSeconds(180)
                )
            )

        verify(diskCache).saveAccessToken(ssoUrl, accessToken)
    }

    @Test
    fun getAccessTokenWithoutCaches() {
        val expirationClientRegistration = clock.instant().plusSeconds(120)

        ssoOidcClient.stub {
            on(
                ssoOidcClient.registerClient(
                    RegisterClientRequest.builder()
                        .clientType("public")
                        .clientName("aws-toolkit-jetbrains-${Instant.now(clock)}")
                        .build()
                )
            ).thenReturn(
                RegisterClientResponse.builder()
                    .clientId(clientId)
                    .clientSecret(clientSecret)
                    .clientSecretExpiresAt(expirationClientRegistration.toEpochMilli())
                    .build()
            )

            on(
                ssoOidcClient.startDeviceAuthorization(
                    StartDeviceAuthorizationRequest.builder()
                        .clientId(clientId)
                        .clientSecret(clientSecret)
                        .startUrl(ssoUrl)
                        .build()
                )
            ).thenReturn(
                StartDeviceAuthorizationResponse.builder()
                    .expiresIn(120)
                    .deviceCode("dummyCode")
                    .userCode("dummyUserCode")
                    .verificationUri("someUrl")
                    .verificationUriComplete("someUrlComplete")
                    .build()
            )

            on(
                ssoOidcClient.createToken(
                    CreateTokenRequest.builder()
                        .clientId(clientId)
                        .clientSecret(clientSecret)
                        .deviceCode("dummyCode")
                        .grantType("urn:ietf:params:oauth:grant-type:device_code")
                        .build()
                )
            ).thenReturn(
                CreateTokenResponse.builder()
                    .accessToken("accessToken")
                    .expiresIn(180)
                    .build()
            )
        }

        val accessToken = accessTokenProvider.accessToken()
        assertThat(accessToken).usingRecursiveComparison()
            .isEqualTo(
                AccessToken(
                    ssoUrl,
                    ssoRegion,
                    "accessToken",
                    clock.instant().plusSeconds(180)
                )
            )

        verify(diskCache).saveClientRegistration(ssoRegion, ClientRegistration(clientId, clientSecret, expirationClientRegistration))
        verify(diskCache).saveAccessToken(ssoUrl, accessToken)
    }

    @Test
    fun getAccessTokenWithoutCachesMultiplePolls() {
        val expirationClientRegistration = clock.instant().plusSeconds(120)

        diskCache.stub {
            on(
                diskCache.loadClientRegistration(ssoRegion)
            ).thenReturn(
                ClientRegistration(clientId, clientSecret, expirationClientRegistration)
            )
        }

        ssoOidcClient.stub {
            on(
                ssoOidcClient.startDeviceAuthorization(
                    StartDeviceAuthorizationRequest.builder()
                        .clientId(clientId)
                        .clientSecret(clientSecret)
                        .startUrl(ssoUrl)
                        .build()
                )
            ).thenReturn(
                StartDeviceAuthorizationResponse.builder()
                    .expiresIn(120)
                    .deviceCode("dummyCode")
                    .userCode("dummyUserCode")
                    .verificationUri("someUrl")
                    .verificationUriComplete("someUrlComplete")
                    .interval(1)
                    .build()
            )

            on(
                ssoOidcClient.createToken(
                    CreateTokenRequest.builder()
                        .clientId(clientId)
                        .clientSecret(clientSecret)
                        .deviceCode("dummyCode")
                        .grantType("urn:ietf:params:oauth:grant-type:device_code")
                        .build()
                )
            ).thenThrow(
                AuthorizationPendingException.builder().build()
            ).thenReturn(
                CreateTokenResponse.builder()
                    .accessToken("accessToken")
                    .expiresIn(180)
                    .build()
            )
        }

        val startTime = Instant.now()
        val accessToken = accessTokenProvider.accessToken()
        val callDuration = Duration.between(startTime, Instant.now())

        assertThat(accessToken).usingRecursiveComparison()
            .isEqualTo(
                AccessToken(
                    ssoUrl,
                    ssoRegion,
                    "accessToken",
                    clock.instant().plusSeconds(180)
                )
            )

        assertThat(callDuration.toSeconds()).isGreaterThanOrEqualTo(1).isLessThan(2)

        verify(diskCache).saveAccessToken(ssoUrl, accessToken)
    }

    @Test
    fun exceptionStopsPolling() {
        val expirationClientRegistration = clock.instant().plusSeconds(120)

        ssoOidcClient.stub {
            diskCache.stub {
                on(
                    diskCache.loadClientRegistration(ssoRegion)
                ).thenReturn(
                    ClientRegistration(clientId, clientSecret, expirationClientRegistration)
                )
            }

            on(
                ssoOidcClient.startDeviceAuthorization(
                    StartDeviceAuthorizationRequest.builder()
                        .clientId(clientId)
                        .clientSecret(clientSecret)
                        .startUrl(ssoUrl)
                        .build()
                )
            ).thenReturn(
                StartDeviceAuthorizationResponse.builder()
                    .expiresIn(120)
                    .deviceCode("dummyCode")
                    .userCode("dummyUserCode")
                    .verificationUri("someUrl")
                    .verificationUriComplete("someUrlComplete")
                    .build()
            )

            on(
                ssoOidcClient.createToken(
                    CreateTokenRequest.builder()
                        .clientId(clientId)
                        .clientSecret(clientSecret)
                        .deviceCode("dummyCode")
                        .grantType("urn:ietf:params:oauth:grant-type:device_code")
                        .build()
                )
            ).thenThrow(
                InvalidRequestException.builder().build()
            )
        }

        assertThatThrownBy { accessTokenProvider.accessToken() }.isInstanceOf(InvalidRequestException::class.java)
    }

    @Test
    fun backOffTimeIsRespected() {
        val expirationClientRegistration = clock.instant().plusSeconds(120)

        ssoOidcClient.stub {
            diskCache.stub {
                on(
                    diskCache.loadClientRegistration(ssoRegion)
                ).thenReturn(
                    ClientRegistration(clientId, clientSecret, expirationClientRegistration)
                )
            }

            on(
                ssoOidcClient.startDeviceAuthorization(
                    StartDeviceAuthorizationRequest.builder()
                        .clientId(clientId)
                        .clientSecret(clientSecret)
                        .startUrl(ssoUrl)
                        .build()
                )
            ).thenReturn(
                StartDeviceAuthorizationResponse.builder()
                    .expiresIn(120)
                    .deviceCode("dummyCode")
                    .userCode("dummyUserCode")
                    .verificationUri("someUrl")
                    .verificationUriComplete("someUrlComplete")
                    .interval(1)
                    .build()
            )

            on(
                ssoOidcClient.createToken(
                    CreateTokenRequest.builder()
                        .clientId(clientId)
                        .clientSecret(clientSecret)
                        .deviceCode("dummyCode")
                        .grantType("urn:ietf:params:oauth:grant-type:device_code")
                        .build()
                )
            ).thenThrow(
                SlowDownException.builder().build()
            ).thenReturn(
                CreateTokenResponse.builder()
                    .accessToken("accessToken")
                    .expiresIn(180)
                    .build()
            )
        }

        val startTime = Instant.now()
        val accessToken = accessTokenProvider.accessToken()
        val callDuration = Duration.between(startTime, Instant.now())

        assertThat(accessToken).usingRecursiveComparison()
            .isEqualTo(
                AccessToken(
                    ssoUrl,
                    ssoRegion,
                    "accessToken",
                    clock.instant().plusSeconds(180)
                )
            )

        assertThat(callDuration.toSeconds()).isGreaterThanOrEqualTo(6).isLessThan(7)

        verify(diskCache).saveAccessToken(ssoUrl, accessToken)
    }

    @Test
    fun failToGetClientRegistrationLeadsToError() {
        ssoOidcClient.stub {
            on(
                ssoOidcClient.registerClient(any<RegisterClientRequest>())
            ).thenThrow(SsoOidcException.builder().build())
        }

        assertThatThrownBy { accessTokenProvider.accessToken() }.isInstanceOf(SsoOidcException::class.java)
    }
}
