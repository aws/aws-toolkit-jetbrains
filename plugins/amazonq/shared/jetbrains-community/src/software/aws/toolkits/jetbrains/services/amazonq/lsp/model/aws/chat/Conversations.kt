// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat

// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

typealias FilterValue = String

data class TextBasedFilterOption(
    val type: String,
    val placeholder: String?,
    val icon: IconType?,
) {
    companion object {
        fun create(type: TextInputType, placeholder: String, icon: IconType): TextBasedFilterOption =
            TextBasedFilterOption(type.value, placeholder, icon)
    }
}

data class FilterOption(
    val id: String,
    val type: String,
    val placeholder: String?,
    val icon: IconType?,
)

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

enum class TextInputType(val value: String) {
    TEXTAREA("textarea"),
    TEXTINPUT("textinput"),
    ;

    override fun toString(): String =
        name.lowercase()
}

enum class ConversationAction(val value: String) {
    DELETE("delete"),
    EXPORT("markdown"),
    ;

    override fun toString(): String =
        name.lowercase()
}
data class ConversationClickParams(
    val id: String,
    val action: String?,
) {
    companion object {
        fun create(id: String, action: ConversationAction): ConversationClickParams =
            ConversationClickParams(id, action.value)
    }
}

data class ConversationClickResult(
    val id: String,
    val action: String?,
    val success: Boolean,
) {
    companion object {
        fun create(id: String, action: ConversationAction, success: Boolean): ConversationClickResult =
            ConversationClickResult(id, action.value, success)
    }
}

data class ListConversationsRequest(
    override val command: String,
    override val params: ListConversationsParams,
) : ChatNotification<ListConversationsParams>

data class ConversationClickRequest(
    override val command: String,
    override val params: ConversationClickParams,
) : ChatNotification<ConversationClickParams>
