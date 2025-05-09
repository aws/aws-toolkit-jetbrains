// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonq.lsp.flareChat

import com.intellij.openapi.application.ApplicationManager
import com.intellij.util.messages.Topic
import java.util.EventListener

interface AsyncChatUiListener : EventListener {
    @Deprecated("shouldn't need this version")
    fun onChange(command: String) {}

    fun onChange(command: FlareUiMessage) {}

    companion object {
        @Topic.AppLevel
        val TOPIC = Topic.create("Partial chat message provider", AsyncChatUiListener::class.java)

        fun notifyPartialMessageUpdate(command: FlareUiMessage) {
            ApplicationManager.getApplication().messageBus.syncPublisher(TOPIC).onChange(command)
        }

        @Deprecated("shouldn't need this version")
        fun notifyPartialMessageUpdate(command: String) {
            ApplicationManager.getApplication().messageBus.syncPublisher(TOPIC).onChange(command)
        }
    }
}
