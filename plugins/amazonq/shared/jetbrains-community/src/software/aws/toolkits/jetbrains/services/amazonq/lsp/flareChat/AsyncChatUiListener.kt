// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonq.lsp.flareChat

import com.intellij.openapi.application.ApplicationManager
import com.intellij.util.messages.Topic
import java.util.EventListener

interface AsyncChatUiListener : EventListener {
    fun onChange(message: String) {}

    companion object {
        @Topic.AppLevel
        val TOPIC = Topic.create("Partial chat message provider", AsyncChatUiListener::class.java)

        fun notifyPartialMessageUpdate(message: String) {
            ApplicationManager.getApplication().messageBus.syncPublisher(TOPIC).onChange(message)
        }
    }
}
