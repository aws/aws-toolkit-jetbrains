// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat

data class OpenFileDiffParams(
    val originalFileUri: String,
    val originalFileContent: String? = null,
    val isDeleted: Boolean,
    val fileContent: String? = null,
)
