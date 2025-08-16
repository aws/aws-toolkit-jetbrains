// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cwc.commands.codescan.actions

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.DumbAware
import kotlinx.coroutines.runBlocking
import software.aws.toolkits.jetbrains.services.amazonq.lsp.flareChat.ChatCommunicationManager
import software.aws.toolkits.jetbrains.services.amazonq.lsp.flareChat.FlareUiMessage
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat.ChatPrompt
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat.SEND_TO_PROMPT
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat.SendToPromptParams
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat.TriggerType
import software.aws.toolkits.jetbrains.services.cwc.controller.chat.telemetry.TelemetryHelper
import software.aws.toolkits.jetbrains.services.cwc.controller.chat.telemetry.getStartUrl

class HandleIssueCommandAction : AnAction(), DumbAware {
    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.project != null
    }
    fun createLineRangeText(issueContext: MutableMap<String, String>): String {
        val startLine = issueContext["startLine"]
        val endLine = issueContext["endLine"]
        return if (startLine.equals(endLine)) {
            "[$startLine]"
        } else {
            "[$startLine, $endLine]"
        }
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val contextDataKey = DataKey.create<MutableMap<String, String>>("amazonq.codescan.handleIssueCommandContext")
        val actionDataKey = DataKey.create<String>("amazonq.codescan.handleIssueCommandAction")
        val context = e.getData(contextDataKey) ?: return
        val action = e.getData(actionDataKey) ?: return

        // Emit telemetry event
        TelemetryHelper.recordTelemetryIssueCommandAction(
            context["findingId"].orEmpty(),
            context["detectorId"].orEmpty(),
            context["ruleId"].orEmpty(),
            context["autoDetected"].orEmpty(),
            getStartUrl(project).orEmpty(),
            action, // The action name (explainIssue or applyFix)
            "Succeeded"
        )

        ActionManager.getInstance().getAction("q.openchat").actionPerformed(e)

        ApplicationManager.getApplication().executeOnPooledThread {
            runBlocking {
                // https://github.com/aws/aws-toolkit-vscode/blob/master/packages/amazonq/src/lsp/chat/commands.ts#L30
                val codeSelection = "\n```\n${context["code"]?.trimIndent()?.trim()}\n```\n"
                val actionString = if (action == "explainIssue") "Explain" else "Fix"

                val prompt = "$actionString ${context["title"]} issue in ${context["fileName"]} at ${createLineRangeText(context)}"

                val modelPrompt = "$actionString ${context["title"]} issue in ${context["fileName"]} at ${createLineRangeText(context)}" +
                    "Issue: \"${context}\" \n"

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
                ChatCommunicationManager.getInstance(project).notifyUi(uiMessage)
//                AsyncChatUiListener.notifyPartialMessageUpdate(project, uiMessage)
            }
        }
    }
}
