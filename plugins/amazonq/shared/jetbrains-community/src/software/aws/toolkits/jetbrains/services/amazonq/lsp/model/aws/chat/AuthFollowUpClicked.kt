// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat

import com.fasterxml.jackson.annotation.JsonValue
import com.google.gson.annotations.JsonAdapter
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.EnumJsonValueAdapter

data class AuthFollowUpClickNotification(
    override val command: String,
    override val params: AuthFollowUpClickedParams,
) : ChatNotification<AuthFollowUpClickedParams>

data class AuthFollowUpClickedParams(
    val tabId: String,
    val messageId: String,
    val authFollowupType: AuthFollowupType,
)

@JsonAdapter(EnumJsonValueAdapter::class)
enum class AuthFollowupType(@JsonValue val repr: String) {
    FULL_AUTH("full-auth"),
    RE_AUTH("re-auth"),
    MISSING_SCOPES("missing_scopes"),
    USE_SUPPORTED_AUTH("use-supported-auth"),
    ;
}
