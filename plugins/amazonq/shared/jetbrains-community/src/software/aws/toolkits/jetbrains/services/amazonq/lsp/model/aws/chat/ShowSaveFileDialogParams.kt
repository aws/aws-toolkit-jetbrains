// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat

import java.net.URI

data class ShowSaveFileDialogParams(
    val supportedFormats: List<String>,
    val defaultURI: String?
)

data class ShowSaveFileDialogResult(
    val target: URI
)
