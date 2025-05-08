// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat

data class TabBarActionParams(
    val tabId: String?,
    val action: String,
) {
    companion object {
        fun create(tabId: String?, action: TabBarAction): TabBarActionParams =
            TabBarActionParams(tabId, action.value)
    }
}

enum class TabBarAction(val value: String) {
    EXPORT("export"),
    ;

    override fun toString(): String =
        name.lowercase()
}

data class TabBarActionResult(
    val success: Boolean,
)

data class TabBarActionRequest(
    override val command: String,
    override val params: TabBarActionParams,
) : ChatNotification<TabBarActionParams>
