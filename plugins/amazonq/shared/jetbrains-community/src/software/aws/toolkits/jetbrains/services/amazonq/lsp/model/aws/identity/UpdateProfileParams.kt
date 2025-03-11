// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.identity

class UpdateProfileParams(
    val profile: Profile,
    val ssoSession: SsoSession,
    val options: UpdateProfileOptions
)
