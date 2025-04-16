// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat

data class CopyCodeToClipboardNotification(
    val command: String,
    val params: CopyCodeToClipboardParams,
)

data class CopyCodeToClipboardParams(
    val tabId: String,
    val messageId: String,
    val code: String? = null,
    val type: String? = null,
    val referenceTrackerInformation: List<ReferenceTrackerInformation>? = null,
    val eventId: String? = null,
    val codeBlockIndex: Int? = null,
    val totalCodeBlocks: Int? = null,
)
