// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat

import com.fasterxml.jackson.annotation.JsonValue
import com.google.gson.annotations.JsonAdapter
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.EnumJsonValueAdapter
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat.Status as ResultStatus
// MCP Server Status
@JsonAdapter(EnumJsonValueAdapter::class)
enum class McpServerStatus(@JsonValue val repr: String) {
    INITIALIZING("textarea"),
    ENABLED("textinput"),
    FAILED("failed"),
    DISABLED("disabled");
}

// List MCP Servers Parameters
data class ListMcpServersParams(
    val filter: Map<String, FilterValue>? = null
)

// List MCP Servers Result
data class ListMcpServersResult(
    val header: Header? = null,
    val list: List<DetailedListGroup>,
    val filterOptions: List<FilterOption>? = null
) {
    data class Header(
        val title: String,
        val description: String? = null
    )
}


// MCP Server Click Parameters
data class McpServerClickParams(
    val id: String,
    val title: String? = null,
    // here Any is sufficient to serialize/deserialze complex objects
    val optionsValues: Map<String, Any>? = null
)




// MCP Server Click Result
data class McpServerClickResult(
    val id: String,
    val title: String? = null,
    val optionsValues: Map<String, String>? = null,
    val filterOptions: List<FilterOption>? = null,
    val filterActions: List<Button>? = null,
    val list: List<DetailedListGroup>? = null,
    val header: Header? = null
) {
    data class Header(
        val title: String? = null,
        val icon: IconType? = null,
        val status: Status? = null,
        val description: String? = null,
        val actions: List<Action>? = null
    ) {
        data class Status(
            val icon: IconType? = null,
            val title: String? = null,
            val description: String? = null,
            val status: ResultStatus? = null
        )
    }
}


data class DetailedListGroup(
    val groupName: String? = null,
    val children: List<DetailedListItem>? = null,
    val actions: List<Action>? = null,
    val icon: IconType? = null
)

data class DetailedListItem(
    val title: String,
    val description: String? = null,
    val groupActions: Boolean? = null,
    val children: List<DetailedListGroup>? = null
)


