// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat

data class ShowSaveFileDialogParams(
    val supportedFormats: List<String>,
    val defaultUri: String?,
)

data class ShowSaveFileDialogResult(
    val targetUri: String,
)
