// Copyright 2021 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.core.credentials.sso

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.intellij.collaboration.auth.credentials.Credentials
import software.amazon.awssdk.auth.token.credentials.SdkToken
import software.amazon.awssdk.services.sso.SsoClient
import software.amazon.awssdk.services.ssooidc.SsoOidcClient
import software.aws.toolkits.core.utils.SensitiveField
import software.aws.toolkits.core.utils.redactedString
import java.time.Instant
import java.util.Optional

/**
 * Access token returned from [SsoOidcClient.createToken] used to retrieve AWS Credentials from [SsoClient.getRoleCredentials].
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.DEDUCTION)
@JsonSubTypes(value = [JsonSubTypes.Type(DeviceAuthorizationGrantToken::class), JsonSubTypes.Type(PKCEAuthorizationGrantToken::class) ])
interface AccessToken : SdkToken, Credentials {
    val ssoUrl: String
    val region: String

    @SensitiveField
    override val accessToken: String

    @SensitiveField
    @get:JsonInclude(JsonInclude.Include.NON_NULL)
    val refreshToken: String?

    val expiresAt: Instant
    val createdAt: Instant

    override fun token() = accessToken

    override fun expirationTime() = Optional.of(expiresAt)
}

data class DeviceAuthorizationGrantToken(
    val startUrl: String,
    override val region: String,
    override val accessToken: String,
    override val refreshToken: String? = null,
    override val expiresAt: Instant,
    override val createdAt: Instant = Instant.EPOCH
) : AccessToken {
    override val ssoUrl: String
        get() = startUrl

    override fun toString() = redactedString(this)
}

data class PKCEAuthorizationGrantToken(
    val issuerUrl: String,
    override val region: String,
    override val accessToken: String,
    override val refreshToken: String?,
    override val expiresAt: Instant,
    override val createdAt: Instant
) : AccessToken {
    override val ssoUrl: String
        get() = issuerUrl

    override fun toString() = redactedString(this)
}

// diverging from SDK/CLI impl here since they do: sha1sum(sessionName ?: startUrl)
// which isn't good enough for us
// only used in scoped case
data class AccessTokenCacheKey(
    val connectionId: String,
    val startUrl: String,
    val scopes: List<String>
)
