// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonqCodeScan

import software.aws.toolkits.jetbrains.services.amazonqCodeScan.messages.Button
import software.aws.toolkits.jetbrains.services.amazonqCodeScan.messages.CodeScanButtonId
import software.aws.toolkits.jetbrains.services.amazonqCodeScan.messages.CodeScanChatMessage
import software.aws.toolkits.jetbrains.services.amazonqCodeScan.messages.CodeScanChatMessageContent
import software.aws.toolkits.jetbrains.services.amazonqCodeScan.messages.ProgressField
import software.aws.toolkits.jetbrains.services.amazonqCodeScan.messages.PromptProgressMessage
import software.aws.toolkits.jetbrains.services.codewhisperer.codescan.CodeWhispererCodeScanIssue
import software.aws.toolkits.jetbrains.services.codewhisperer.codescan.utils.IssueSeverity
import software.aws.toolkits.jetbrains.services.cwc.controller.chat.StaticPrompt
import software.aws.toolkits.jetbrains.services.cwc.controller.chat.StaticTextResponse
import software.aws.toolkits.jetbrains.services.cwc.messages.ChatMessageType
import software.aws.toolkits.resources.message
import java.util.UUID

private val projectScanButton = Button(
    id = CodeScanButtonId.StartProjectScan.id,
    text = message("codescan.chat.message.button.projectScan")
)

private val fileScanButton = Button(
    id = CodeScanButtonId.StartFileScan.id,
    text = message("codescan.chat.message.button.fileScan")
)

private val openIssuesPanelButton = Button(
    id = CodeScanButtonId.OpenIssuesPanel.id,
    text = message("codescan.chat.message.button.openIssues"),
    keepCardAfterClick = true
)

fun buildStartNewScanChatContent() = CodeScanChatMessageContent(
    type = ChatMessageType.Answer,
    message = message("codescan.chat.new_scan.input.message"),
    buttons = listOf(
        fileScanButton,
        projectScanButton
    ),
    canBeVoted = false
)

// TODO: Replace StaticPrompt and StaticTextResponse message according to Fnf
fun buildHelpChatPromptContent() = CodeScanChatMessageContent(
    type = ChatMessageType.Prompt,
    message = StaticPrompt.Help.message,
    canBeVoted = false
)

fun buildHelpChatAnswerContent() = CodeScanChatMessageContent(
    type = ChatMessageType.Answer,
    message = StaticTextResponse.Help.message,
    canBeVoted = false
)

fun buildUserSelectionProjectScanChatContent() = CodeScanChatMessageContent(
    type = ChatMessageType.Prompt,
    message = message("codescan.chat.message.button.projectScan"),
    canBeVoted = false
)

fun buildUserSelectionFileScanChatContent() = CodeScanChatMessageContent(
    type = ChatMessageType.Prompt,
    message = message("codescan.chat.message.button.fileScan"),
    canBeVoted = false
)

fun buildNotInGitRepoChatContent() = CodeScanChatMessageContent(
    type = ChatMessageType.Answer,
    message = message("codescan.chat.message.not_git_repo"),
    canBeVoted = false
)

fun buildScanInProgressChatContent(currentStep: Int, isProject: Boolean) = CodeScanChatMessageContent(
    type = ChatMessageType.AnswerPart,
    message = buildString {
        appendLine(if (isProject) message("codescan.chat.message.scan_begin_project") else message("codescan.chat.message.scan_begin_file"))
        appendLine("")
        appendLine(message("codescan.chat.message.scan_begin_wait_time"))
        appendLine("")
        appendLine("${getIconForStep(0, currentStep)} " + message("codescan.chat.message.scan_step_1"))
        appendLine("${getIconForStep(1, currentStep)} " + message("codescan.chat.message.scan_step_2"))
        appendLine("${getIconForStep(2, currentStep)} " + message("codescan.chat.message.scan_step_3"))
    }
)

val cancellingProgressField = ProgressField(
    status = "warning",
    text = message("general.canceling"),
    value = -1,
    actions = emptyList()
)

fun buildScanCompleteChatContent(issues: List<CodeWhispererCodeScanIssue>, isProject: Boolean = false): CodeScanChatMessageContent {
    val issueCountMap = IssueSeverity.entries.associate { it.displayName to 0 }.toMutableMap()
    val aggregatedIssues = issues.groupBy { it.severity }
    aggregatedIssues.forEach { (key, list) -> if (list.isNotEmpty()) issueCountMap[key] = list.size }

    val message = buildString {
        appendLine(if (isProject) message("codewhisperer.codescan.scan_complete_project") else message("codewhisperer.codescan.scan_complete_file"))
        issueCountMap.entries.forEach { (severity, count) ->
            appendLine(message("codewhisperer.codescan.scan_complete_count", severity, count))
        }
    }

    return CodeScanChatMessageContent(
        type = ChatMessageType.Answer,
        message = message,
        buttons = listOf(
            openIssuesPanelButton
        ),
    )
}

fun buildPromptProgressMessage(tabId: String, isProject: Boolean = false, isCanceling: Boolean = false) = PromptProgressMessage(
    progressField = when {
        isCanceling -> cancellingProgressField
        isProject -> projectScanProgressField
        else -> fileScanProgressField
    },
    tabId = tabId
)

fun buildClearPromptProgressMessage(tabId: String) = PromptProgressMessage(
    tabId = tabId
)

val runCodeScanMessage
    get() = CodeScanChatMessage(messageType = ChatMessageType.Prompt, command = "review", tabId = UUID.randomUUID().toString())

val cancelFileScanButton = Button(
    id = CodeScanButtonId.StopFileScan.id,
    text = message("general.cancel"),
    icon = "cancel"
)

val cancelProjectScanButton = cancelFileScanButton.copy(
    id = CodeScanButtonId.StopProjectScan.id
)

val fileScanProgressField = ProgressField(
    status = "default",
    text = message("codescan.chat.message.scan_file_in_progress"),
    value = -1,
    actions = listOf(cancelFileScanButton)
)

val projectScanProgressField = fileScanProgressField.copy(
    text = message("codescan.chat.message.scan_project_in_progress"),
    actions = listOf(cancelProjectScanButton)
)

fun buildProjectScanFailedChatContent(errorMessage: String?) = CodeScanChatMessageContent(
    type = ChatMessageType.Answer,
    message = errorMessage ?: message("codescan.chat.message.project_scan_failed")
)

fun getIconForStep(targetStep: Int, currentStep: Int) = when {
    currentStep == targetStep -> "&#9744;"
    currentStep > targetStep -> "&#9745;"
    else -> "&#9744;"
}
