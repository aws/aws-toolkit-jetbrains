// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat

data class CreatePromptParams(
    val promptName: String,
)

data class CreatePromptNotification(
    override val command: String,
    override val params: CreatePromptParams,
) : ChatNotification<CreatePromptParams>
