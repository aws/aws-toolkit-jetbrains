// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0
package software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat

data class InfoLinkClickNotification(
    val command: String,
    val params: InfoLinkClickParams,
)

data class SourceLinkClickNotification(
    val command: String,
    val params: SourceLinkClickParams,
)

data class LinkClickNotification(
    val command: String,
    val params: LinkClickParams,
)

data class InfoLinkClickParams(
    val tabId: String,
    val link: String,
    val eventId: String? = null,
)

data class LinkClickParams(
    val tabId: String,
    val link: String,
    val eventId: String? = null,
    val messageId: String,
)

data class SourceLinkClickParams(
    val tabId: String,
    val link: String,
    val eventId: String? = null,
    val messageId: String,
)
