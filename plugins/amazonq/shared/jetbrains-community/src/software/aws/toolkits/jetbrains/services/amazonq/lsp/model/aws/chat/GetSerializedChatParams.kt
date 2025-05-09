// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat

import com.fasterxml.jackson.annotation.JsonValue
import com.google.gson.annotations.JsonAdapter
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.EnumJsonValueAdapter

data class GetSerializedChatParams(
    val tabId: String,
    val format: String,
)

@JsonAdapter(EnumJsonValueAdapter::class)
enum class SerializedChatFormat(@JsonValue val repr: String) {
    HTML("html"),
    MARKDOWN("markdown"),
    ;
}

data class GetSerializedChatResult(
    val content: String,
)

data class GetSerializedChatRequest(
    val requestId: String,
    override val command: String,
    override val params: GetSerializedChatParams,
) : ChatNotification<GetSerializedChatParams>

data class GetSerializedChatResponseParams(
    val success: Boolean,
    val result: GetSerializedChatResult,
)

data class GetSerializedChatResponse(
    val requestId: String,
    val command: String,
    val params: GetSerializedChatResponseParams,
)
