// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws

import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat.IconType

data class AwsServerCapabilities(
    val chatOptions: ChatOptions,
)

data class ChatOptions(
    val quickActions: QuickActions,
    val history: Boolean,
    val export: Boolean,
    val mcpServers: Boolean,
    val modelSelection: Boolean,
)

data class QuickActions(
    val quickActionsCommandGroups: List<QuickActionsCommandGroups>,
)

data class QuickActionsCommandGroups(
    val commands: List<QuickActionCommand>,
)

open class QuickActionCommand(
    open val command: String,
    open val description: String?,
    open val placeholder: String? = null,
    open val icon: IconType? = null,
)

data class ContextCommand(
    val id: String?,
    val route: List<String>?,
    val label: String?,
    val children: ContextCommandGroup?,
    override val command: String,
    override val description: String?,
    override val placeholder: String? = null,
    override val icon: IconType? = null,
) : QuickActionCommand(
    command = command,
    description = description,
    placeholder = placeholder,
    icon = icon
)

data class ContextCommandGroup(
    val groupName: String?,
    val commands: List<ContextCommand>,
)
