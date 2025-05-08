// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat

data class OpenSettingsParams(
    val settingKey: String,
)

data class OpenSettingsNotification(
    override val command: String,
    override val params: OpenSettingsParams,
) : ChatNotification<OpenSettingsParams>

const val OPEN_WORKSPACE_SETTINGS_KEY = "amazonQ.workspaceIndex"
