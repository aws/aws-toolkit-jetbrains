// Copyright 2022 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.amazon.q.jetbrains.core.credentials.sso.bearer

import com.intellij.openapi.application.ApplicationManager
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.RuleChain
import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.mock
import org.mockito.kotlin.reset
import org.mockito.kotlin.spy
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever
import software.amazon.awssdk.auth.credentials.AnonymousCredentialsProvider
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration
import software.amazon.awssdk.core.exception.SdkException
import software.amazon.awssdk.core.interceptor.Context
import software.amazon.awssdk.core.interceptor.ExecutionAttributes
import software.amazon.awssdk.core.interceptor.ExecutionInterceptor
import software.amazon.awssdk.core.internal.InternalCoreExecutionAttribute
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.ssooidc.SsoOidcClient
import software.amazon.awssdk.services.ssooidc.model.AccessDeniedException
import software.amazon.awssdk.services.ssooidc.model.CreateTokenRequest
import software.amazon.awssdk.services.ssooidc.model.CreateTokenResponse
import software.amazon.awssdk.services.ssooidc.model.InvalidGrantException
import software.amazon.q.jetbrains.core.AwsClientManager
import software.amazon.q.jetbrains.core.MockClientManager
import software.amazon.q.jetbrains.core.MockClientManagerRule
import software.amazon.q.jetbrains.core.credentials.sono.SONO_URL
import software.amazon.q.jetbrains.core.credentials.sso.AccessTokenCacheKey
import software.amazon.q.jetbrains.core.credentials.sso.DeviceAuthorizationClientRegistration
import software.amazon.q.jetbrains.core.credentials.sso.DeviceAuthorizationClientRegistrationCacheKey
import software.amazon.q.jetbrains.core.credentials.sso.DeviceAuthorizationGrantToken
import software.amazon.q.jetbrains.core.credentials.sso.DeviceGrantAccessTokenCacheKey
import software.amazon.q.jetbrains.core.credentials.sso.DiskCache
import software.amazon.q.jetbrains.core.credentials.sso.PKCEAccessTokenCacheKey
import software.aws.toolkits.core.region.aRegionId
import software.aws.toolkits.core.utils.test.aString
import java.time.Clock
import java.time.Instant
import java.time.temporal.ChronoUnit

class InteractiveBearerTokenProviderTest {
    val applicationRule = ApplicationRule()
    val mockClientManager = MockClientManagerRule()

    @Rule
    @JvmField
    val ruleChain = RuleChain(
        applicationRule,
        mockClientManager
    )

    @Rule
    @JvmField
    val disposableRule = DisposableRule()

    private lateinit var oidcClient: SsoOidcClient
    private val diskCache = mock<DiskCache>()
    private val startUrl = aString()
    private val region = aRegionId()
    private val scopes = listOf("scope1", "scope2")

    @Before
    fun setUp() {
        oidcClient = mockClientManager.create<SsoOidcClient>()
    }

    @After
    fun tearDown() {
        oidcClient.close()
    }

    @Test
    fun `oidcClient does not retry on InvalidGrantException failure`() {
        fun verifyRetryAttempts(configuration: ClientOverrideConfiguration.Builder) {
            configuration.addExecutionInterceptor(
                object : ExecutionInterceptor {
                    override fun onExecutionFailure(context: Context.FailedExecution?, executionAttributes: ExecutionAttributes?) {
                        super.onExecutionFailure(context, executionAttributes)

                        assertThat(executionAttributes?.getAttribute(InternalCoreExecutionAttribute.EXECUTION_ATTEMPT)).isEqualTo(1)
                    }
                }
            )
        }
        fun buildUnmanagedSsoOidcClientForTests(region: String): SsoOidcClient =
            AwsClientManager.getInstance()
                .createUnmanagedClient(
                    AnonymousCredentialsProvider.create(),
                    Region.of(region),
                    clientCustomizer = { _, _, _, _, configuration ->
                        verifyRetryAttempts(ssoOidcClientConfigurationBuilder(configuration))
                    }
                )

        MockClientManager.useRealImplementations(disposableRule.disposable)
        oidcClient.close()
        oidcClient = spy(buildUnmanagedSsoOidcClientForTests("us-east-1"))
        val registerClientResponse = oidcClient.registerClient {
            it.clientType("public")
            it.scopes(scopes)
            it.clientName("test")
        }
        val deviceAuthorizationResponse = oidcClient.startDeviceAuthorization {
            it.clientId(registerClientResponse.clientId())
            it.clientSecret(registerClientResponse.clientSecret())
            it.startUrl(SONO_URL)
        }
        assertThrows<InvalidGrantException> {
            oidcClient.createToken {
                it.clientId(registerClientResponse.clientId())
                it.clientSecret(registerClientResponse.clientSecret())
                it.deviceCode(deviceAuthorizationResponse.deviceCode() + "invalid")
                it.grantType("urn:ietf:params:oauth:grant-type:device_code")
            }
        }
    }

    @Test
    fun `oidcClient has detailed error message on InvalidGrantException failure`() {
        fun buildUnmanagedSsoOidcClientForTests(region: String): SsoOidcClient =
            AwsClientManager.getInstance()
                .createUnmanagedClient(
                    AnonymousCredentialsProvider.create(),
                    Region.of(region),
                    clientCustomizer = { _, _, _, _, configuration ->
                        ssoOidcClientConfigurationBuilder(configuration)
                    }
                )

        MockClientManager.useRealImplementations(disposableRule.disposable)
        oidcClient.close()
        oidcClient = spy(buildUnmanagedSsoOidcClientForTests("us-east-1"))
        val exception = assertThrows<InvalidGrantException> {
            oidcClient.createToken {
                it.clientId("test")
                it.clientSecret("test")
                it.deviceCode("invalid for test")
                it.grantType("urn:ietf:params:oauth:grant-type:device_code")
            }
        }

        assertThat(exception.message)
            .isEqualTo("invalid_grant: Invalid device code provided (Service: SsoOidc, Status Code: 400, Request ID: ${exception.requestId()})")
    }

    @Test
    fun `resolveToken doesn't refresh if token was retrieved recently`() {
        stubClientRegistration()
        whenever(diskCache.loadAccessToken(any<DeviceGrantAccessTokenCacheKey>())).thenReturn(
            DeviceAuthorizationGrantToken(
                startUrl = startUrl,
                region = region,
                accessToken = "accessToken",
                refreshToken = "refreshToken",
                expiresAt = Instant.now().plus(1, ChronoUnit.HOURS)
            )
        )
        val sut = buildSut()
        sut.resolveToken()
    }

    @Test
    fun `resolveToken attempts to refresh token on first invoke if expired`() {
        stubClientRegistration()
        stubAccessToken()
        whenever(diskCache.loadAccessToken(any<DeviceGrantAccessTokenCacheKey>())).thenReturn(
            DeviceAuthorizationGrantToken(
                startUrl = startUrl,
                region = region,
                accessToken = "accessToken",
                refreshToken = "refreshToken",
                expiresAt = Instant.now()
            )
        )
        val sut = buildSut()
        sut.resolveToken()

        verify(oidcClient).createToken(any<CreateTokenRequest>())
    }

    @Test
    fun `resolveToken refreshes on subsequent invokes if expired`() {
        val mockClock = mock<Clock>()
        whenever(mockClock.instant()).thenReturn(Instant.now())
        stubClientRegistration()
        stubAccessToken()
        whenever(diskCache.loadAccessToken(any<DeviceGrantAccessTokenCacheKey>())).thenReturn(
            DeviceAuthorizationGrantToken(
                startUrl = startUrl,
                region = region,
                accessToken = "accessToken",
                refreshToken = "refreshToken",
                expiresAt = Instant.now().plus(1, ChronoUnit.HOURS)
            )
        )
        val sut = buildSut(mockClock)
        // current token should be valid
        assertThat(sut.resolveToken().accessToken).isEqualTo("accessToken")
        verify(oidcClient, times(0)).createToken(any<CreateTokenRequest>())

        // then if we advance the clock it should refresh
        whenever(mockClock.instant()).thenReturn(Instant.now().plus(100, ChronoUnit.DAYS))
        assertThat(sut.resolveToken().accessToken).isEqualTo("access1")
        verify(oidcClient, times(1)).createToken(any<CreateTokenRequest>())
    }

    @Test
    fun `resolveToken throws if reauthentication is needed`() {
        stubClientRegistration()
        stubAccessToken()
        reset(oidcClient)
        whenever(oidcClient.createToken(any<CreateTokenRequest>())).thenThrow(AccessDeniedException.create("denied", null))

        val sut = buildSut()
        assertThrows<SdkException> { sut.resolveToken() }
    }

    @Test
    fun `invalidate notifies listeners of update`() {
        val mockListener = mock<BearerTokenProviderListener>()
        val conn = ApplicationManager.getApplication().messageBus.connect()
        conn.subscribe(BearerTokenProviderListener.TOPIC, mockListener)

        stubClientRegistration()
        stubAccessToken()
        val sut = buildSut()
        sut.invalidate()

        verify(mockListener).onProviderChange(sut.id)
    }

    @Test
    fun `invalidate clears correctly`() {
        stubClientRegistration()
        stubAccessToken()
        val sut = buildSut()
        whenever(diskCache.loadAccessToken(any<DeviceGrantAccessTokenCacheKey>())).thenReturn(null)
        sut.invalidate()

        // initial load
        // invalidate attempts to reload token from disk
        verify(diskCache, times(2)).loadAccessToken(any<DeviceGrantAccessTokenCacheKey>())
        verify(diskCache).loadAccessToken(any<PKCEAccessTokenCacheKey>())
        verify(diskCache).invalidateClientRegistration(region)
        verify(diskCache).invalidateAccessToken(startUrl)

        // clears out on-disk token
        verify(diskCache, times(2)).invalidateAccessToken(
            argThat<AccessTokenCacheKey> {
                when (this) {
                    is DeviceGrantAccessTokenCacheKey -> startUrl == this.startUrl && scopes == this.scopes
                    is PKCEAccessTokenCacheKey -> startUrl == this.issuerUrl && scopes == this.scopes
                }
            }
        )

        // nothing else
        verifyNoMoreInteractions(diskCache)

        // should not have a token now
        assertThat(sut.currentToken()?.accessToken).isNull()
        assertThrows<NoTokenInitializedException> { sut.resolveToken() }
    }

    @Test
    fun `reauthenticate updates current token`() {
        stubClientRegistration()
        stubAccessToken()
        val sut = buildSut()

        assertThat(sut.resolveToken().accessToken).isEqualTo("access1")

        // and now instead of trying to stub out the entire OIDC device flow, abuse the fact that we short-circuit and read from disk if available
        reset(diskCache)
        whenever(diskCache.loadAccessToken(any<DeviceGrantAccessTokenCacheKey>())).thenReturn(
            DeviceAuthorizationGrantToken(
                startUrl = startUrl,
                region = region,
                accessToken = "access1234",
                refreshToken = "refresh1234",
                expiresAt = Instant.MAX
            )
        )
        sut.reauthenticate()

        assertThat(sut.resolveToken().accessToken).isEqualTo("access1234")
    }

    @Test
    fun `reauthenticate notifies listeners of update`() {
        val mockListener = mock<BearerTokenProviderListener>()
        val conn = ApplicationManager.getApplication().messageBus.connect()
        conn.subscribe(BearerTokenProviderListener.TOPIC, mockListener)

        stubClientRegistration()
        stubAccessToken()
        val sut = buildSut()
        sut.reauthenticate()

        // once for invalidate, once after the token has been retrieved
        verify(mockListener, times(2)).onProviderChange(sut.id)
    }

    private fun buildSut(clock: Clock = Clock.systemUTC()) = InteractiveBearerTokenProvider(
        startUrl = startUrl,
        region = region,
        scopes = scopes,
        cache = diskCache,
        id = "test",
        clock = clock,
    )

    private fun stubClientRegistration() {
        whenever(diskCache.loadClientRegistration(any<DeviceAuthorizationClientRegistrationCacheKey>(), any())).thenReturn(
            DeviceAuthorizationClientRegistration(
                "",
                "",
                Instant.MAX
            )
        )
    }

    private fun stubAccessToken() {
        whenever(diskCache.loadAccessToken(any<DeviceGrantAccessTokenCacheKey>())).thenReturn(
            DeviceAuthorizationGrantToken(
                startUrl = startUrl,
                region = region,
                accessToken = "accessToken",
                refreshToken = "refreshToken",
                expiresAt = Instant.now().minus(100, ChronoUnit.DAYS),
            )
        )
        whenever(oidcClient.createToken(any<CreateTokenRequest>())).thenReturn(
            CreateTokenResponse.builder()
                .refreshToken("refresh1")
                .accessToken("access1")
                .expiresIn(1800)
                .build()
        )
    }
}
