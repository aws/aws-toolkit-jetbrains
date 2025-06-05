// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat

import com.fasterxml.jackson.annotation.JsonValue
import com.google.gson.annotations.JsonAdapter
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.EnumJsonValueAdapter

data class TabBarActionParams(
    val tabId: String?,
    val action: String,
)

@JsonAdapter(EnumJsonValueAdapter::class)
enum class TabBarAction(@JsonValue val repr: String) {
    EXPORT("export"),
}

data class TabBarActionResult(
    val success: Boolean,
)

data class TabBarActionRequest(
    override val command: String,
    override val params: TabBarActionParams,
) : ChatNotification<TabBarActionParams>
