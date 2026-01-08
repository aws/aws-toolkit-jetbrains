// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cfnlsp.protocol

data class UpdateCredentialsParams(
    val data: String,
    val encrypted: Boolean = true
)

data class UpdateCredentialsResult(
    val success: Boolean
)
