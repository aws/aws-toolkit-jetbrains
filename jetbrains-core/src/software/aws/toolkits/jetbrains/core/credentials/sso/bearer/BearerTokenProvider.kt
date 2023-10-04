// Copyright 2022 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.core.credentials.sso.bearer

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.util.containers.orNull
import software.amazon.awssdk.auth.credentials.AnonymousCredentialsProvider
import software.amazon.awssdk.auth.token.credentials.SdkToken
import software.amazon.awssdk.auth.token.credentials.SdkTokenProvider
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration
import software.amazon.awssdk.core.retry.conditions.OrRetryCondition
import software.amazon.awssdk.core.retry.conditions.RetryOnExceptionsCondition
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.ssooidc.SsoOidcClient
import software.amazon.awssdk.services.ssooidc.SsoOidcTokenProvider
import software.amazon.awssdk.services.ssooidc.internal.OnDiskTokenManager
import software.amazon.awssdk.services.ssooidc.model.InvalidGrantException
import software.amazon.awssdk.utils.SdkAutoCloseable
import software.amazon.awssdk.utils.cache.CachedSupplier
import software.amazon.awssdk.utils.cache.NonBlocking
import software.amazon.awssdk.utils.cache.RefreshResult
import software.aws.toolkits.core.ToolkitClientCustomizer
import software.aws.toolkits.core.clients.nullDefaultProfileFile
import software.aws.toolkits.core.credentials.ToolkitBearerTokenProvider
import software.aws.toolkits.core.credentials.ToolkitBearerTokenProviderDelegate
import software.aws.toolkits.jetbrains.core.AwsClientManager
import software.aws.toolkits.jetbrains.core.credentials.diskCache
import software.aws.toolkits.jetbrains.core.credentials.sso.AccessToken
import software.aws.toolkits.jetbrains.core.credentials.sso.DiskCache
import software.aws.toolkits.jetbrains.core.credentials.sso.SsoAccessTokenProvider
import java.time.Duration
import java.time.Instant
import java.util.concurrent.atomic.AtomicReference

internal interface BearerTokenLogoutSupport

interface BearerTokenProvider : SdkTokenProvider, SdkAutoCloseable, ToolkitBearerTokenProviderDelegate {
    /**
     * @return The best available [SdkToken] to the provider without making network calls or prompting for user input
     */
    fun currentToken(): AccessToken?

    /**
     * @return The authentication state of [currentToken]
     */
    fun state(): BearerTokenAuthState = state(currentToken())

    /**
     * Request provider to interactively request user input to obtain a new [AccessToken]
     */
    open fun reauthenticate() {
        throw UnsupportedOperationException("Provider is not interactive and cannot reauthenticate")
    }

    open fun supportsLogout() = this is BearerTokenLogoutSupport

    open fun invalidate() {
        throw UnsupportedOperationException("Provider is not interactive and cannot be invalidated")
    }

    companion object {
        internal fun tokenExpired(accessToken: AccessToken) = Instant.now().isAfter(accessToken.expiresAt)

        internal fun state(accessToken: AccessToken?) = when {
            accessToken == null -> BearerTokenAuthState.NOT_AUTHENTICATED
            tokenExpired(accessToken) -> {
                if (accessToken.refreshToken != null) {
                    BearerTokenAuthState.NEEDS_REFRESH
                } else {
                    // token is invalid if there is no refresh token
                    BearerTokenAuthState.NOT_AUTHENTICATED
                }
            }
            else -> BearerTokenAuthState.AUTHORIZED
        }
    }
}

class InteractiveBearerTokenProvider(
    startUrl: String,
    region: String,
    scopes: List<String>,
    cache: DiskCache = diskCache
) : BearerTokenProvider, BearerTokenLogoutSupport, Disposable {
    override val id = ToolkitBearerTokenProvider.ssoIdentifier(startUrl, region)
    override val displayName = ToolkitBearerTokenProvider.ssoDisplayName(startUrl)

    private val ssoOidcClient: SsoOidcClient = buildUnmanagedSsoOidcClient(region)
    private val accessTokenProvider =
        SsoAccessTokenProvider(
            startUrl,
            region,
            cache,
            ssoOidcClient,
            scopes = scopes
        )

    private val supplier = CachedSupplier.builder { refreshToken() }.prefetchStrategy(NonBlocking("AWS SSO bearer token refresher")).build()
    private val lastToken = AtomicReference<AccessToken?>()
    init {
        lastToken.set(cache.loadAccessToken(accessTokenProvider.accessTokenCacheKey))

        ApplicationManager.getApplication().messageBus.connect(this).subscribe(
            BearerTokenProviderListener.TOPIC,
            object : BearerTokenProviderListener {
                override fun invalidate(providerId: String) {
                    if (id == providerId) {
                        invalidate()
                    }
                }
            }
        )
    }

    private fun refreshToken(): RefreshResult<out SdkToken> {
        val lastToken = lastToken.get() ?: error("Token refresh started before session initialized")
        val token = if (Duration.between(Instant.now(), lastToken.expiresAt) > Duration.ofMinutes(30)) {
            lastToken
        } else {
            accessTokenProvider.refreshToken(lastToken).also {
                this.lastToken.set(it)
            }
        }

        return RefreshResult.builder(token)
            .staleTime(token.expiresAt.minus(DEFAULT_STALE_DURATION))
            .prefetchTime(token.expiresAt.minus(DEFAULT_PREFETCH_DURATION))
            .build()
    }

    override fun resolveToken() = supplier.get()

    override fun close() {
        ssoOidcClient.close()
        supplier.close()
    }

    override fun dispose() {
        close()
    }

    override fun currentToken() = lastToken.get()

    override fun invalidate() {
        accessTokenProvider.invalidate()
        lastToken.set(null)
        BearerTokenProviderListener.notifyCredUpdate(id)
    }

    override fun reauthenticate() {
        // we probably don't need to invalidate this, but we might as well since we need to login again anyways
        invalidate()
        accessTokenProvider.accessToken().also {
            lastToken.set(it)
            BearerTokenProviderListener.notifyCredUpdate(id)
        }
    }
}

public enum class BearerTokenAuthState {
    AUTHORIZED,
    NEEDS_REFRESH,
    NOT_AUTHENTICATED
}

class ProfileSdkTokenProviderWrapper(private val sessionName: String, region: String) : BearerTokenProvider, Disposable {
    override val id = ToolkitBearerTokenProvider.diskSessionIdentifier(sessionName)
    override val displayName = ToolkitBearerTokenProvider.diskSessionDisplayName(sessionName)

    private val sdkTokenManager = OnDiskTokenManager.create(sessionName)
    private val ssoOidcClient = lazy { buildUnmanagedSsoOidcClient(region) }
    private val tokenProvider = lazy {
        SsoOidcTokenProvider.builder()
            .ssoOidcClient(ssoOidcClient.value)
            .sessionName(sessionName)
            .staleTime(DEFAULT_STALE_DURATION)
            .prefetchTime(DEFAULT_PREFETCH_DURATION)
            .build()
    }

    override fun resolveToken(): SdkToken = tokenProvider.value.resolveToken()

    override fun currentToken(): AccessToken? = sdkTokenManager.loadToken().orNull()?.let {
        AccessToken(
            startUrl = it.startUrl(),
            region = it.region(),
            accessToken = it.token(),
            refreshToken = it.refreshToken(),
            expiresAt = it.expirationTime().orElseThrow()
        )
    }

    override fun close() {
        sdkTokenManager.close()
        if (ssoOidcClient.isInitialized()) {
            ssoOidcClient.value.close()
        }
        if (tokenProvider.isInitialized()) {
            tokenProvider.value.close()
        }
    }

    override fun dispose() {
        close()
    }
}

internal val DEFAULT_STALE_DURATION = Duration.ofMinutes(15)
internal val DEFAULT_PREFETCH_DURATION = Duration.ofMinutes(20)

val ssoOidcClientConfigurationBuilder: (ClientOverrideConfiguration.Builder) -> ClientOverrideConfiguration.Builder = { configuration ->
    configuration.nullDefaultProfileFile()

    // Get the existing RetryPolicy
    val existingRetryPolicy = configuration.retryPolicy()

    // Add InvalidGrantException to the RetryOnExceptionsCondition
    val updatedRetryPolicy = existingRetryPolicy.toBuilder()
        .retryCondition(
            OrRetryCondition.create(
                existingRetryPolicy.retryCondition(),
                RetryOnExceptionsCondition.create(setOf(InvalidGrantException::class.java)),
            )
        ).build()

    // Update the RetryPolicy in the configuration
    configuration.retryPolicy(updatedRetryPolicy)
}

fun buildUnmanagedSsoOidcClient(region: String): SsoOidcClient =
    AwsClientManager.getInstance()
        .createUnmanagedClient(
            AnonymousCredentialsProvider.create(),
            Region.of(region),
            clientCustomizer = ToolkitClientCustomizer { _, _, _, _, configuration ->
                ssoOidcClientConfigurationBuilder(configuration)
            }
        )
