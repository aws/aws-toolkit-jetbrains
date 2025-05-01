// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat

data class GetSerializedChatParams(
    val tabId: String,
    val format: String,
) {
    companion object {
        fun create(tabId: String, format: SerializedChatFormat): GetSerializedChatParams =
            GetSerializedChatParams(tabId, format.value)
    }
}

enum class SerializedChatFormat(val value: String) {
    HTML("html"),
    MARKDOWN("markdown"),
    ;

    override fun toString(): String =
        name.lowercase()
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
