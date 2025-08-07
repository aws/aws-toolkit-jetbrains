// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonq.lsp.flareChat

data class FlareUiMessage(
    val command: String,
    val params: Any,
    val requestId: String? = null,
    val tabId: String? = null,
    val isPartialResult: Boolean? = false
)
