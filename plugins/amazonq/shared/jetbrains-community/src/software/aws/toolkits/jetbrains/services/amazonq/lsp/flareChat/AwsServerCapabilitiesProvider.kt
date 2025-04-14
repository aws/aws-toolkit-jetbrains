// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonq.lsp.flareChat

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project

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
                            Command("/help", "Learn more about Amazon Q then"),
                            Command("/clear", "Clear this session")
                        )
                    )
                )
            )
        )
    }
}

data class AwsServerCapabilities(
    val chatOptions: ChatOptions,
)

data class ChatOptions(
    val quickActions: QuickActions,
)

data class QuickActions(
    val quickActionsCommandGroups: List<QuickActionsCommandGroups>,
)

data class QuickActionsCommandGroups(
    val commands: List<Command>,
)

data class Command(
    val command: String,
    val description: String,
)
