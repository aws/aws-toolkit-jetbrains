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
    DISABLED("disabled"),
}
