// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat

import com.fasterxml.jackson.annotation.JsonValue
import com.google.gson.annotations.JsonAdapter
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.EnumJsonValueAdapter

typealias FilterValue = String

data class TextBasedFilterOption(
    val type: String,
    val placeholder: String?,
    val icon: IconType?,
    val title: String?,
    val description: String?,
)


data class FilterOption(
    val id: String,
    val placeholder: String? = null,
    val title: String? = null,
    val description: String? = null,
    val icon: IconType? = null,
    val type: String,
    val options: List<Option>? = null
) {
    data class Option(
        val value: String,
        val label: String
    )
}

data class Action(
    val id: String,
    val icon: IconType?,
    val text: String,
)

data class ConversationItem(
    val id: String,
    val icon: IconType?,
    val description: String?,
    val actions: List<Action>?,
)

data class ConversationItemGroup(
    val groupName: String?,
    val icon: IconType?,
    val items: List<ConversationItem>?,
)

data class ListConversationsParams(
    val filter: Map<String, FilterValue>?,
)

data class ConversationsList(
    val header: Header?,
    val filterOptions: List<FilterOption>?,
    val list: List<ConversationItemGroup>,
) {
    data class Header(
        val title: String,
    )
}

typealias ListConversationsResult = ConversationsList

@JsonAdapter(EnumJsonValueAdapter::class)
enum class TextInputType(@JsonValue val repr: String) {
    TEXTAREA("textarea"),
    TEXTINPUT("textinput"),
}

@JsonAdapter(EnumJsonValueAdapter::class)
enum class ConversationAction(@JsonValue val repr: String) {
    DELETE("delete"),
    EXPORT("markdown"),
}

data class ConversationClickParams(
    val id: String,
    val action: String?,
)

data class ConversationClickResult(
    val id: String,
    val action: String?,
    val success: Boolean,
)

data class ListConversationsRequest(
    override val command: String,
    override val params: ListConversationsParams,
) : ChatNotification<ListConversationsParams>

data class ConversationClickRequest(
    override val command: String,
    override val params: ConversationClickParams,
) : ChatNotification<ConversationClickParams>
