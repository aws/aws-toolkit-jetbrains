// Copyright 2023 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0
@file:Suppress("BannedImports")
package software.aws.toolkits.jetbrains.services.cwc.commands

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.runBlocking
import software.aws.toolkits.jetbrains.services.amazonq.lsp.flareChat.AsyncChatUiListener
import software.aws.toolkits.jetbrains.services.amazonq.lsp.flareChat.FlareUiMessage
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat.GENERIC_COMMAND
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat.GenericCommandParams
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat.SEND_TO_PROMPT
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat.SendToPromptParams
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat.TriggerType
import software.aws.toolkits.jetbrains.services.amazonq.messages.AmazonQMessage
import software.aws.toolkits.jetbrains.services.cwc.editor.context.ActiveFileContextExtractor
import software.aws.toolkits.jetbrains.services.cwc.editor.context.ExtractionTriggerType

// Register Editor Actions in the Editor Context Menu
class ActionRegistrar {

    private val _messages by lazy { MutableSharedFlow<AmazonQMessage>(extraBufferCapacity = 10) }
    val flow = _messages.asSharedFlow()

    fun reportMessageClick(command: EditorContextCommand, project: Project) {
        // new agentic chat route
        ApplicationManager.getApplication().executeOnPooledThread {
            runBlocking {
                val contextExtractor = ActiveFileContextExtractor.create(fqnWebviewAdapter = null, project = project)
                val fileContext = contextExtractor.extractContextForTrigger(ExtractionTriggerType.ContextMenu)
                val codeSelection = "\n```\n${fileContext.focusAreaContext?.codeSelection?.trimIndent()?.trim()}\n```\n"
                var uiMessage: FlareUiMessage? = null
                if (command.verb != SEND_TO_PROMPT) {
                    val params = GenericCommandParams(selection = codeSelection, triggerType = TriggerType.CONTEXT_MENU, genericCommand = command.name)
                    uiMessage = FlareUiMessage(command = GENERIC_COMMAND, params = params)
                } else {
                    val params = SendToPromptParams(selection = codeSelection, triggerType = TriggerType.CONTEXT_MENU)
                    uiMessage = FlareUiMessage(command = SEND_TO_PROMPT, params = params)
                }
                AsyncChatUiListener.notifyPartialMessageUpdate(project, uiMessage)
            }
        }
    }

    // provide singleton access
    companion object {
        val instance = ActionRegistrar()
    }
}
