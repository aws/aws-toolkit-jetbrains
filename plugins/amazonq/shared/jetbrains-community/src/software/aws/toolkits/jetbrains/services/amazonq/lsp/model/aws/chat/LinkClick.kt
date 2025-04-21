// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0
package software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat

data class InfoLinkClickNotification(
    override val command: String,
    override val params: InfoLinkClickParams,
) : ChatNotification<InfoLinkClickParams>

data class SourceLinkClickNotification(
    override val command: String,
    override val params: SourceLinkClickParams,
) : ChatNotification<SourceLinkClickParams>

data class LinkClickNotification(
    override val command: String,
    override val params: LinkClickParams,
) : ChatNotification<LinkClickParams>

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
