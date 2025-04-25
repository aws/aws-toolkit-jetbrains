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

data class OpenTabResult(
    val tabId: String,
)

data class OpenTabParams(
    val tabId: String? = null,
    val newTabOptions: NewTabOptions? = null,
)

data class NewTabOptions(
    val state: TabState? = null,
    val data: TabData? = null,
)

data class TabState(
    val inProgress: Boolean? = null,
    val cancellable: Boolean? = null,
)

data class TabData(
    val placeholderText: String? = null,
    val messages: List<ChatMessage>,
)
