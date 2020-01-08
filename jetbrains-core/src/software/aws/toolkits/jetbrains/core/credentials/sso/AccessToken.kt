// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.core.credentials.sso

import java.time.Instant

data class AccessToken(
    val startUrl: String,
    val region: String,
    val accessToken: String,
    val expiresAt: Instant
)
