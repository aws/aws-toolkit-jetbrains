// Copyright 2023 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0
@file:Suppress("BannedImports")
package software.aws.toolkits.jetbrains.services.cwc.commands

import com.google.gson.Gson
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.runBlocking
import software.aws.toolkits.jetbrains.services.amazonq.lsp.flareChat.AsyncChatUiListener
import software.aws.toolkits.jetbrains.services.amazonq.lsp.flareChat.FlareUiMessage
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat.ChatPrompt
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat.GenericCommandParams
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat.SendToPromptParams
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat.TriggerType
import software.aws.toolkits.jetbrains.services.amazonq.messages.AmazonQMessage
import software.aws.toolkits.jetbrains.services.amazonqCodeTest.controller.TestCommandMessage
import software.aws.toolkits.jetbrains.services.cwc.editor.context.ActiveFileContextExtractor
import software.aws.toolkits.jetbrains.services.cwc.editor.context.ExtractionTriggerType

// Register Editor Actions in the Editor Context Menu
class ActionRegistrar {

    private val _messages by lazy { MutableSharedFlow<AmazonQMessage>(extraBufferCapacity = 10) }
    val flow = _messages.asSharedFlow()

    fun reportMessageClick(command: EditorContextCommand, project: Project) {
        if (command == EditorContextCommand.GenerateUnitTests) {
            AsyncChatUiListener.notifyPartialMessageUpdate(Gson().toJson(TestCommandMessage()))
        } else {
            // new agentic chat route
            ApplicationManager.getApplication().executeOnPooledThread {
                runBlocking {
                    val contextExtractor = ActiveFileContextExtractor.create(fqnWebviewAdapter = null, project = project)
                    val fileContext = contextExtractor.extractContextForTrigger(ExtractionTriggerType.ContextMenu)
                    val codeSelection = "\n```\n${fileContext.focusAreaContext?.codeSelection?.trimIndent()?.trim()}\n```\n"
                    var uiMessage: FlareUiMessage? = null
                    if (command.verb != "sendToPrompt") {
                        val params = GenericCommandParams(selection = codeSelection, triggerType = TriggerType.CONTEXT_MENU, genericCommand = command.name)
                        uiMessage = FlareUiMessage(command = "genericCommand", params = params)
                    } else {
                        val params = SendToPromptParams(selection = codeSelection, triggerType = TriggerType.CONTEXT_MENU)
                        uiMessage = FlareUiMessage(command = "sendToPrompt", params = params)
                    }
                    AsyncChatUiListener.notifyPartialMessageUpdate(uiMessage)
                }
            }
        }
    }

    fun reportMessageClick(issue: MutableMap<String, String>) {
        ApplicationManager.getApplication().executeOnPooledThread {
            runBlocking {
                handleCodeScanExplainCommand(issue)
            }
        }
    }

    private fun handleCodeScanExplainCommand(issue: MutableMap<String, String>) {
        // https://github.com/aws/aws-toolkit-vscode/blob/master/packages/amazonq/src/lsp/chat/commands.ts#L30
        val codeSelection = "\n```\n${issue["code"]?.trimIndent()?.trim()}\n```\n"

        val prompt = "Explain the following part of my code \n\n " +
            "Issue:    \"${issue["title"]}\" \n" +
            "Code:    $codeSelection"

        val modelPrompt = "Explain the following part of my code \n\n " +
            "Issue:    \"${issue["title"]}\" \n" +
            "Description:    ${issue["description"]} \n" +
            "Code:    $codeSelection and generate code demonstrating the fix"

        val params = SendToPromptParams(
            selection = codeSelection,
            triggerType = TriggerType.CONTEXT_MENU,
            prompt = ChatPrompt(
                prompt = prompt,
                escapedPrompt = modelPrompt,
                command = null
            ),
            autoSubmit = true
        )

        val uiMessage = FlareUiMessage("sendToPrompt", params)
        AsyncChatUiListener.notifyPartialMessageUpdate(uiMessage)
    }

    // provide singleton access
    companion object {
        val instance = ActionRegistrar()
    }
}
