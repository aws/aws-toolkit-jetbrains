// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat

data class ShowOpenFileDialogParams(
    val canSelectFiles: Boolean,
    val canSelectFolders: Boolean,
    val canSelectMany: Boolean,
    val filters: Map<String, List<String>>,
    val title: String?,
)
