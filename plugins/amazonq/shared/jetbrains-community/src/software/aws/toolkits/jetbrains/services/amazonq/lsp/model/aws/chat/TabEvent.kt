// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat

data class TabEventRequest(
    override val command: String,
    override val params: TabEventParams,
) : ChatNotification<TabEventParams>

data class TabEventParams(
    val tabId: String,
)
