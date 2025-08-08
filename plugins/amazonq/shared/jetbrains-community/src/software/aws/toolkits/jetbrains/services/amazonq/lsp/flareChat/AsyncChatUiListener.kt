// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonq.lsp.flareChat

import com.intellij.openapi.project.Project
import com.intellij.util.messages.Topic
import java.util.EventListener

@Deprecated("Why are we using a message bus for this????????")
interface AsyncChatUiListener : EventListener {
    @Deprecated("shouldn't need this version")
    fun onChange(command: String) {}

    fun onChange(command: FlareUiMessage) {}

    companion object {
        @Topic.ProjectLevel
        val TOPIC = Topic.create("Partial chat message provider", AsyncChatUiListener::class.java)

        @Deprecated("Why are we using a message bus for this????????")
        fun notifyPartialMessageUpdate(project: Project, command: FlareUiMessage) {
            project.messageBus.syncPublisher(TOPIC).onChange(command)
        }

        // will be removed in next iteration.
        @Deprecated("shouldn't need this version")
        fun notifyPartialMessageUpdate(project: Project, command: String) {
            project.messageBus.syncPublisher(TOPIC).onChange(command)
        }
    }
}
