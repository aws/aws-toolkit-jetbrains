// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.credentials

data class UpdateCredentialsPayload(
    val data: String,
    val encrypted: Boolean,
)

data class UpdateCredentialsPayloadData(
    val data: BearerCredentials,
)

data class BearerCredentials(
    val token: String,
)
