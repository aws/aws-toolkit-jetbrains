// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

// InteractiveAccessTokenProvider.kt
package software.aws.toolkits.jetbrains.core.credentials.sso

import software.amazon.awssdk.auth.token.credentials.SdkTokenProvider

/**
 * Common interface for interactive access token providers that support authorization tracking
 */
interface InteractiveAccessTokenProvider : SdkTokenProvider {
    /**
     * Current pending authorization state, if any
     */
    val authorization: PendingAuthorization?

    /**
     * Get access token, potentially triggering interactive authentication
     */
    fun accessToken(): AccessToken

    /**
     * Invalidate cached tokens and registrations
     */
    fun invalidate()

    /**
     * Load cached access token without triggering authentication
     */
    fun loadAccessToken(): AccessToken?

    /**
     * Refresh an existing access token using its refresh token
     */
    fun refreshToken(currentToken: AccessToken): AccessToken
}
