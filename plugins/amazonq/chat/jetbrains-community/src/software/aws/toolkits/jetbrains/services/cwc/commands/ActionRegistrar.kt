// Copyright 2023 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cwc.commands

import com.google.gson.Gson
import com.intellij.openapi.project.Project
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.runBlocking
import software.aws.toolkits.jetbrains.services.amazonq.lsp.flareChat.AsyncChatUiListener
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat.GenericCommandParams
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat.TriggerType
import software.aws.toolkits.jetbrains.services.amazonq.messages.AmazonQMessage
import software.aws.toolkits.jetbrains.services.cwc.editor.context.ActiveFileContextExtractor
import software.aws.toolkits.jetbrains.services.cwc.editor.context.ExtractionTriggerType

// Register Editor Actions in the Editor Context Menu
class ActionRegistrar {

    private val _messages by lazy { MutableSharedFlow<AmazonQMessage>(extraBufferCapacity = 10) }
    val flow = _messages.asSharedFlow()

    fun reportMessageClick(command: EditorContextCommand, project: Project) {
        if (command == EditorContextCommand.GenerateUnitTests) {
            // pre-existing old chat code path
            _messages.tryEmit(ContextMenuActionMessage(command, project))
        }
        else {
            // new agentic chat route
            runBlocking {
                val contextExtractor = ActiveFileContextExtractor.create(fqnWebviewAdapter = null, project = project)
                val fileContext = contextExtractor.extractContextForTrigger(ExtractionTriggerType.ContextMenu)
                val codeSelection = "\n```\n${fileContext.focusAreaContext?.codeSelection?.trimIndent()?.trim()}\n```\n"

                val params = GenericCommandParams(selection = codeSelection, triggerType = TriggerType.CONTEXT_MENU, genericCommand = command.name)

                val uiMessage = """
                        {
                            "command": "genericCommand",
                            "params": ${Gson().toJson(params)}
                        }
                    """.trimIndent()
                AsyncChatUiListener.notifyPartialMessageUpdate(uiMessage)
            }
        }

    }

    fun reportMessageClick(command: EditorContextCommand, issue: MutableMap<String, String>, project: Project) {
        _messages.tryEmit(CodeScanIssueActionMessage(command, issue, project))
    }

    // provide singleton access
    companion object {
        val instance = ActionRegistrar()
        
        /**
         * Computes the product of 10 * 10
         * @return The result of 10 * 10
         */
        fun computeTenTimesTen(): Int {
            return 10 * 10
        }
    }
}
