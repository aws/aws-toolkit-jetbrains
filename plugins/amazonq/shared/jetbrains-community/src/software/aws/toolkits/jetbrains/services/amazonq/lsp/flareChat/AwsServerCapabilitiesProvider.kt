// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonq.lsp.flareChat

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat.IconType

@Service(Service.Level.PROJECT)
class AwsServerCapabilitiesProvider {
    private var serverCapabilities: AwsServerCapabilities? = null

    fun setAwsServerCapabilities(serverCapabilities: AwsServerCapabilities?) {
        this.serverCapabilities = serverCapabilities
    }

    fun getChatOptions() = serverCapabilities?.chatOptions ?: DEFAULT_CHAT_OPTIONS

    companion object {
        fun getInstance(project: Project) = project.service<AwsServerCapabilitiesProvider>()

        private val DEFAULT_CHAT_OPTIONS: ChatOptions = ChatOptions(
            QuickActions(
                listOf(
                    QuickActionsCommandGroups(
                        listOf(
                            QuickActionCommand("/help", "Learn more about Amazon Q then"),
                            QuickActionCommand("/clear", "Clear this session")
                        )
                    )
                )
            ),
            history = true,
            export = true,
            mcpServers = true,
            modelSelection = true
        )
    }
}

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
