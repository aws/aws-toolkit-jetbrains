// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat

data class ChatUpdateParams(
    val tabId: String,
    val state: TabState? = null,
    val data: TabData? = null,
)
