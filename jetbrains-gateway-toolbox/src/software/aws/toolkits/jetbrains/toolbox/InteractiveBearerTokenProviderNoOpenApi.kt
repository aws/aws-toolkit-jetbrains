// Copyright 2023 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.toolbox

import software.amazon.awssdk.auth.credentials.AnonymousCredentialsProvider
import software.amazon.awssdk.auth.token.credentials.SdkToken
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.ssooidc.SsoOidcClient
import software.amazon.awssdk.utils.cache.CachedSupplier
import software.amazon.awssdk.utils.cache.NonBlocking
import software.amazon.awssdk.utils.cache.RefreshResult
import software.aws.toolkits.core.credentials.ToolkitBearerTokenProvider
import software.aws.toolkits.jetbrains.core.credentials.diskCache
import software.aws.toolkits.jetbrains.core.credentials.sso.AccessToken
import software.aws.toolkits.jetbrains.core.credentials.sso.DiskCache
import software.aws.toolkits.jetbrains.core.credentials.sso.SsoAccessTokenProvider
import software.aws.toolkits.jetbrains.core.credentials.sso.bearer.BearerTokenLogoutSupport
import software.aws.toolkits.jetbrains.core.credentials.sso.bearer.BearerTokenProvider
import software.aws.toolkits.jetbrains.core.credentials.sso.bearer.BearerTokenProviderListener
import software.aws.toolkits.jetbrains.core.credentials.sso.bearer.DEFAULT_PREFETCH_DURATION
import software.aws.toolkits.jetbrains.core.credentials.sso.bearer.DEFAULT_STALE_DURATION
import software.aws.toolkits.jetbrains.core.credentials.sso.bearer.buildUnmanagedSsoOidcClient
import java.time.Duration
import java.time.Instant
import java.util.concurrent.atomic.AtomicReference

class InteractiveBearerTokenProviderNoOpenApi(
    startUrl: String,
    region: String,
    scopes: List<String>,
    cache: DiskCache = diskCache
) : BearerTokenProvider, BearerTokenLogoutSupport {
    override val id = ToolkitBearerTokenProvider.ssoIdentifier(startUrl, region)
    override val displayName = ToolkitBearerTokenProvider.ssoDisplayName(startUrl)

    private val ssoOidcClient: SsoOidcClient = SsoOidcClient.builder()
        .credentialsProvider(AnonymousCredentialsProvider.create())
        .region(Region.of(region))
        .build()

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

    override fun currentToken() = lastToken.get()

    override fun invalidate() {
        accessTokenProvider.invalidate()
        lastToken.set(null)
    }

    override fun reauthenticate() {
        // we probably don't need to invalidate this, but we might as well since we need to login again anyways
        invalidate()
        accessTokenProvider.accessToken().also {
            lastToken.set(it)
        }
    }
}
