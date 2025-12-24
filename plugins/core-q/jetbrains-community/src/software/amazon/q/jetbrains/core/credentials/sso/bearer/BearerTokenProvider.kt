// Copyright 2022 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.amazon.q.jetbrains.core.credentials.sso.bearer

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.util.containers.orNull
import software.amazon.awssdk.auth.credentials.AnonymousCredentialsProvider
import software.amazon.awssdk.auth.token.credentials.SdkToken
import software.amazon.awssdk.auth.token.credentials.SdkTokenProvider
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration
import software.amazon.awssdk.core.interceptor.Context
import software.amazon.awssdk.core.interceptor.ExecutionAttributes
import software.amazon.awssdk.core.interceptor.ExecutionInterceptor
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.ssooidc.SsoOidcClient
import software.amazon.awssdk.services.ssooidc.SsoOidcTokenProvider
import software.amazon.awssdk.services.ssooidc.internal.OnDiskTokenManager
import software.amazon.awssdk.services.ssooidc.model.InvalidGrantException
import software.amazon.awssdk.services.ssooidc.model.SsoOidcException
import software.amazon.awssdk.utils.SdkAutoCloseable
import software.amazon.awssdk.utils.cache.CachedSupplier
import software.amazon.awssdk.utils.cache.NonBlocking
import software.amazon.awssdk.utils.cache.RefreshResult
import software.amazon.q.jetbrains.core.AwsClientManager
import software.amazon.q.jetbrains.core.credentials.diskCache
import software.amazon.q.jetbrains.core.credentials.sso.AccessToken
import software.amazon.q.jetbrains.core.credentials.sso.DeviceAuthorizationGrantToken
import software.amazon.q.jetbrains.core.credentials.sso.DiskCache
import software.amazon.q.jetbrains.core.credentials.sso.PendingAuthorization
import software.amazon.q.jetbrains.core.credentials.sso.SsoAccessTokenProvider
import software.amazon.q.jetbrains.core.credentials.sso.bearer.BearerTokenProviderListener.Companion.TOPIC
import software.amazon.q.core.ToolkitClientCustomizer
import software.amazon.q.core.clients.nullDefaultProfileFile
import software.amazon.q.core.credentials.ToolkitBearerTokenProvider
import software.amazon.q.core.credentials.ToolkitBearerTokenProviderDelegate
import software.amazon.q.core.utils.getLogger
import software.amazon.q.core.utils.warn
import java.time.Clock
import java.time.Duration
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import java.util.function.Supplier

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
    fun reauthenticate() {
        throw UnsupportedOperationException("Provider is not interactive and cannot reauthenticate")
    }

    fun supportsLogout() = this is BearerTokenLogoutSupport

    fun invalidate() {
        throw UnsupportedOperationException("Provider is not interactive and cannot be invalidated")
    }

    companion object {
        private fun tokenExpired(accessToken: AccessToken, clock: Clock) = clock.instant().isAfter(accessToken.expiresAt)

        internal fun state(accessToken: AccessToken?, clock: Clock = Clock.systemUTC()) = when {
            accessToken == null -> BearerTokenAuthState.NOT_AUTHENTICATED
            tokenExpired(accessToken, clock) -> {
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
    val startUrl: String,
    val region: String,
    val scopes: List<String>,
    override val id: String,
    cache: DiskCache = diskCache,
    private val clock: Clock = Clock.systemUTC(),
) : BearerTokenProvider, BearerTokenLogoutSupport, Disposable {
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

    private var supplier = supplier()

    val pendingAuthorization: PendingAuthorization?
        get() = accessTokenProvider.authorization

    init {
        ApplicationManager.getApplication().messageBus.connect(this).subscribe(
            TOPIC,
            object : BearerTokenProviderListener {
                override fun invalidate(providerId: String) {
                    if (id == providerId) {
                        invalidate()
                    }
                }

                override fun onProviderChange(providerId: String, newScopes: List<String>?) {
                    newScopes?.let {
                        if (id == providerId && it.toSet() != scopes.toSet()) {
                            invalidate()
                        }
                    }
                }
            }
        )
    }

    private data class SupplierHolder(
        val supplier: SupplierWithInitialValue,
        val cachedSupplier: CachedSupplier<AccessToken>,
    )

    private fun supplier(initialValue: AccessToken? = null) =
        SupplierWithInitialValue(initialValue, accessTokenProvider).let {
            SupplierHolder(
                it,
                CachedSupplier.builder(it).clock(clock).prefetchStrategy(NonBlocking("AWS SSO bearer token refresher")).build()
            )
        }

    private inner class SupplierWithInitialValue(
        initial: AccessToken?,
        val accessTokenProvider: SsoAccessTokenProvider,
    ) : Supplier<RefreshResult<AccessToken>> {
        private val hasCalledAtLeastOnce = AtomicBoolean(false)
        private val initialValue = initial ?: accessTokenProvider.loadAccessToken()
        val lastToken = AtomicReference<AccessToken?>(initialValue)

        // we need to seed CachedSupplier with an initial value, then subsequent calls need to hit the network
        override fun get(): RefreshResult<AccessToken> {
            val token = if (hasCalledAtLeastOnce.getAndSet(true)) {
                refresh()
            } else {
                // on initial call, refresh if needed
                if (initialValue != null && initialValue.expiresAt.minus(DEFAULT_PREFETCH_DURATION) < clock.instant()) {
                    refresh()
                } else {
                    initialValue ?: throw NoTokenInitializedException("Token provider initialized with no token")
                }
            }

            return RefreshResult.builder(token)
                .staleTime(token.expiresAt.minus(DEFAULT_STALE_DURATION))
                .prefetchTime(token.expiresAt.minus(DEFAULT_PREFETCH_DURATION))
                .build()
        }

        fun refresh(): AccessToken {
            val lastToken = lastToken.get() ?: throw NoTokenInitializedException("Token refresh started before session initialized")
            return try {
                accessTokenProvider.refreshToken(lastToken).also {
                    this.lastToken.set(it)
                    ApplicationManager.getApplication().messageBus.syncPublisher(TOPIC).onTokenModified(id)
                }
            } catch (e: InvalidGrantException) {
                LOG.warn { "Invalidated token due to $e" }
                invalidate()

                throw e
            }
        }
    }

    override fun state() = BearerTokenProvider.state(currentToken(), clock)

    // how we expect consumers to obtain a token
    override fun resolveToken() = supplier.cachedSupplier.get()

    override fun close() {
        ssoOidcClient.close()
        supplier.cachedSupplier.close()
    }

    override fun dispose() {
        close()
    }

    // internal nonsense so we can query the token without triggering a refresh
    override fun currentToken() = supplier.supplier.lastToken.get()

    override fun invalidate() {
        accessTokenProvider.invalidate()
        supplier.cachedSupplier.close()
        supplier = supplier()
        BearerTokenProviderListener.notifyCredUpdate(id)
    }

    override fun reauthenticate() {
        // we probably don't need to invalidate this, but we might as well since we need to login again anyways
        invalidate()
        accessTokenProvider.accessToken().also {
            supplier.cachedSupplier.close()
            supplier = supplier(it)
            BearerTokenProviderListener.notifyCredUpdate(id)
        }
    }

    companion object {
        private val LOG = getLogger<InteractiveBearerTokenProvider>()
    }
}

class NoTokenInitializedException(message: String) : Exception(message)

enum class BearerTokenAuthState {
    AUTHORIZED,
    NEEDS_REFRESH,
    NOT_AUTHENTICATED,
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
        DeviceAuthorizationGrantToken(
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

    configuration.addExecutionInterceptor(object : ExecutionInterceptor {
        override fun modifyException(context: Context.FailedExecution, executionAttributes: ExecutionAttributes): Throwable {
            val exception = context.exception()
            if (exception !is SsoOidcException) {
                return exception
            }

            // SSO OIDC service generally has useful messages in the "errorDescription" field, but this is considered non-standard,
            // so Java SDK does not find it and instead provides a generic default exception string
            try {
                val clazz = exception::class.java
                val errorDescription = clazz.methods.firstOrNull { it.name == "errorDescription" }?.invoke(exception) as? String
                    ?: return exception

                // include the type of exception so we don't lose that information if we're only looking at the message and not the stack trace
                val oidcError = clazz.methods.firstOrNull { it.name == "error" }?.invoke(exception) as? String
                    ?: exception.message?.substringBeforeLast('(')?.trimEnd() ?: clazz.name

                return exception.toBuilder().message("$oidcError: $errorDescription").build()
            } catch (e: Exception) {
                getLogger<BearerTokenProvider>().warn(e) { "Encountered error while augmenting service error message" }
                return exception
            }
        }
    })
}

fun buildUnmanagedSsoOidcClient(region: String): SsoOidcClient =
    AwsClientManager.getInstance()
        .createUnmanagedClient(
            AnonymousCredentialsProvider.create(),
            Region.of(region),
            clientCustomizer = ToolkitClientCustomizer { _, _, _, _, configuration ->
                configuration.apiCallTimeout(Duration.ofSeconds(12))
                ssoOidcClientConfigurationBuilder(configuration)
            }
        )
