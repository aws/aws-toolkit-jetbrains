// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cwc.commands.codescan.actions

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.DumbAware
import kotlinx.coroutines.runBlocking
import software.aws.toolkits.jetbrains.services.amazonq.lsp.flareChat.AsyncChatUiListener
import software.aws.toolkits.jetbrains.services.amazonq.lsp.flareChat.FlareUiMessage
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat.ChatPrompt
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat.SEND_TO_PROMPT
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat.SendToPromptParams
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat.TriggerType

class ExplainCodeIssueAction : AnAction(), DumbAware {
    override fun actionPerformed(e: AnActionEvent) {
        val issueDataKey = DataKey.create<MutableMap<String, String>>("amazonq.codescan.explainissue")
        val issueContext = e.getData(issueDataKey) ?: return

        ActionManager.getInstance().getAction("q.openchat").actionPerformed(e)

        ApplicationManager.getApplication().executeOnPooledThread {
            runBlocking {
                // https://github.com/aws/aws-toolkit-vscode/blob/master/packages/amazonq/src/lsp/chat/commands.ts#L30
                val codeSelection = "\n```\n${issueContext["code"]?.trimIndent()?.trim()}\n```\n"

                val prompt = "Explain the following part of my code \n\n " +
                    "Issue:    \"${issueContext["title"]}\" \n" +
                    "Code:    $codeSelection"

                val modelPrompt = "Explain the following part of my code \n\n " +
                    "Issue:    \"${issueContext["title"]}\" \n" +
                    "Description:    ${issueContext["description"]} \n" +
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

                val uiMessage = FlareUiMessage(SEND_TO_PROMPT, params)
                AsyncChatUiListener.notifyPartialMessageUpdate(uiMessage)
            }
        }
    }
}
