// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.core.credentials.sso

import java.time.Instant

data class ClientRegistration(
    val clientId: String,
    val clientSecret: String,
    val expiresAt: Instant
)
