// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat

data class AuthFollowUpClickNotification(
    override val command: String,
    override val params: AuthFollowUpClickedParams,
) : ChatNotification<AuthFollowUpClickedParams>

data class AuthFollowUpClickedParams(
    val tabId: String,
    val messageId: String,
    val authFollowupType: String,
) {
    companion object {
        fun create(tabId: String, messageId: String, authType: AuthFollowupType): AuthFollowUpClickedParams =
            AuthFollowUpClickedParams(tabId, messageId, authType.value)
    }
}

enum class AuthFollowupType(val value: String) {
    FULL_AUTH("full-auth"),
    RE_AUTH("re-auth"),
    MISSING_SCOPES("missing_scopes"),
    USE_SUPPORTED_AUTH("use-supported-auth"),
    ;

    override fun toString(): String =
        name.lowercase()
}
