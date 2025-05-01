// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat

// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

typealias FilterValue = String

data class TextBasedFilterOption(
    val type: TextInputType,
    val placeholder: String? = null,
    val icon: IconType? = null,
)

data class FilterOption(
    val id: String,
    val type: String,
    val placeholder: String? = null,
    val icon: IconType? = null,
)

data class Action(
    val id: String,
    val icon: IconType? = null,
    val text: String,
)

data class ConversationItem(
    val id: String,
    val icon: IconType? = null,
    val description: String? = null,
    val actions: List<Action>? = null,
)

data class ConversationItemGroup(
    val groupName: String? = null,
    val icon: IconType? = null,
    val items: List<ConversationItem>? = null,
)

data class ListConversationsParams(
    val filter: Map<String, FilterValue>? = null,
)

data class ConversationsList(
    val header: Header? = null,
    val filterOptions: List<FilterOption>? = null,
    val list: List<ConversationItemGroup>,
) {
    data class Header(
        val title: String,
    )
}

typealias ListConversationsResult = ConversationsList

enum class TextInputType {
    TEXTAREA,
    TEXTINPUT;

    val value: String
        get() = name.lowercase()

    companion object {
        private val stringToEnum: Map<String, TextInputType> = TextInputType.entries.associateBy { it.name.lowercase() }

        fun fromString(value: String): TextInputType = stringToEnum[value] ?: throw IllegalArgumentException("Unknown IconType: $value")
    }
}

enum class ConversationAction {
    DELETE,
    EXPORT,
    OPEN;

    val value: String
        get() = name.lowercase()

    companion object {
        private val stringToEnum: Map<String, ConversationAction> = ConversationAction.entries.associateBy { it.name.lowercase() }

        fun fromString(value: String): ConversationAction = stringToEnum[value] ?: throw IllegalArgumentException("Unknown IconType: $value")
    }
}

data class ConversationClickParams(
    val id: String,
    val action: ConversationAction? = null,
)

data class ConversationClickResult(
    val id: String,
    val action: ConversationAction? = null,
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
