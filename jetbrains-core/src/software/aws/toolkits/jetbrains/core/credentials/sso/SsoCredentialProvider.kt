// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.core.credentials.sso

import software.amazon.awssdk.auth.credentials.AwsCredentials
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials
import software.amazon.awssdk.services.sso.SsoClient
import software.amazon.awssdk.utils.cache.CachedSupplier
import software.amazon.awssdk.utils.cache.RefreshResult
import java.time.Duration
import java.time.Instant

class SsoCredentialProvider(
    private val ssoAccount: String,
    private val ssoRole: String,
    private val ssoClient: SsoClient,
    private val ssoAccessTokenProvider: SsoAccessTokenProvider
) : AwsCredentialsProvider {
    private val sessionCache: CachedSupplier<SsoCredentialsHolder> = CachedSupplier.builder<SsoCredentialsHolder>(this::refreshCredentials).build()

    override fun resolveCredentials(): AwsCredentials = sessionCache.get().credentials

    private fun refreshCredentials(): RefreshResult<SsoCredentialsHolder> {
        val roleCredentials = ssoClient.getRoleCredentials {
            it.accessToken(ssoAccessTokenProvider.accessToken().accessToken)
            it.accountId(ssoAccount)
            it.roleName(ssoRole)
        }

        val awsCredentials = AwsSessionCredentials.create(
            roleCredentials.roleCredentials().accessKeyId(),
            roleCredentials.roleCredentials().secretAccessKey(),
            roleCredentials.roleCredentials().sessionToken()
        )

        val expirationTime = Instant.ofEpochSecond(roleCredentials.roleCredentials().expiration())

        val ssoCredentials = SsoCredentialsHolder(awsCredentials, expirationTime)

        return RefreshResult.builder<SsoCredentialsHolder>(ssoCredentials)
            .staleTime(expirationTime.minus(Duration.ofMinutes(1)))
            .prefetchTime(expirationTime.minus(Duration.ofMinutes(5)))
            .build()
    }

    private data class SsoCredentialsHolder(val credentials: AwsSessionCredentials, val expirationTime: Instant)
}
