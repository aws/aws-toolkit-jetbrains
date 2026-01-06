// Copyright 2022 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.amazon.q.jetbrains.core.credentials.sso.bearer

import software.amazon.q.jetbrains.core.credentials.sso.DeviceAuthorizationGrantToken
import software.amazon.q.core.region.aRegionId
import software.amazon.q.core.utils.test.aString
import java.time.Instant

fun anAccessToken(refreshToken: String? = aString(), expiresAt: Instant) = DeviceAuthorizationGrantToken(
    startUrl = aString(),
    region = aRegionId(),
    accessToken = aString(),
    refreshToken = refreshToken,
    expiresAt = expiresAt
)
