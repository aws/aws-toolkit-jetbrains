// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat

data class FileClickNotification(
    override val command: String,
    override val params: FileClickParams,
) : ChatNotification<FileClickParams>

data class FileClickParams(
    val tabId: String,
    val filePath: String,
    val action: FileAction? = null,
    val messageId: String? = null,
    val fullPath: String? = null,
)

enum class FileAction {
    ACCEPT_CHANGE,
    REJECT_CHANGE,
}
