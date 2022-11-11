// Copyright 2021 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.core.credentials.sso

import com.fasterxml.jackson.annotation.JsonInclude
import software.amazon.awssdk.auth.token.credentials.SdkToken
import software.amazon.awssdk.services.sso.SsoClient
import software.amazon.awssdk.services.ssooidc.SsoOidcClient
import java.time.Instant
import java.util.Optional

/**
 * Access token returned from [SsoOidcClient.createToken] used to retrieve AWS Credentials from [SsoClient.getRoleCredentials].
 */
data class AccessToken(
    val startUrl: String,
    val region: String,
    val accessToken: String,
    @JsonInclude(JsonInclude.Include.NON_NULL)
    val refreshToken: String? = null,
    val expiresAt: Instant
) : SdkToken {
    override fun token() = accessToken

    override fun expirationTime() = Optional.of(expiresAt)
}

// diverging from SDK/CLI impl here since they do: sha1sum(sessionName ?: startUrl)
// which isn't good enough for us
// only used in scoped case
data class AccessTokenCacheKey(
    val connectionId: String,
    val startUrl: String,
    val scopes: List<String>
)
