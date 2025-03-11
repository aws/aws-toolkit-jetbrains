// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.identity

class SsoSettings(
    val sso_start_url: String,
    val sso_region: String,
    val sso_registration_scopes: List<String>
)
