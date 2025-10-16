// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonqCodeScan.messages

import com.fasterxml.jackson.annotation.JsonProperty
import software.aws.toolkits.jetbrains.services.amazonq.auth.AuthFollowUpType
import software.aws.toolkits.jetbrains.services.amazonq.messages.AmazonQMessage
import software.aws.toolkits.jetbrains.services.cwc.messages.ChatMessageType
import software.aws.toolkits.jetbrains.services.cwc.messages.FollowUp
import java.time.Instant
import java.util.UUID

const val CODE_SCAN_TAB_NAME = "codescan"

enum class CodeScanButtonId(val id: String) {
    StartProjectScan("codescan_start_project_scan"),
    StartFileScan("codescan_start_file_scan"),
    StopProjectScan("codescan_stop_project_scan"),
    StopFileScan("codescan_stop_file_scan"),
    OpenIssuesPanel("codescan_open_issues"),
}

data class Button(
    val id: String,
    val text: String,
    val description: String? = null,
    val icon: String? = null,
    val keepCardAfterClick: Boolean? = false,
    val disabled: Boolean? = false,
    val waitMandatoryFormItems: Boolean? = false,
)
data class ProgressField(
    val title: String? = null,
    val value: Int? = null,
    val status: String? = null,
    val actions: List<Button>? = null,
    val text: String? = null,
)

data class FormItemOption(
    val label: String,
    val value: String,
)

data class FormItem(
    val id: String,
    val type: String = "select",
    val title: String,
    val mandatory: Boolean = true,
    val options: List<FormItemOption> = emptyList(),
)

sealed interface CodeScanBaseMessage : AmazonQMessage

// === UI -> App Messages ===
sealed interface IncomingCodeScanMessage : CodeScanBaseMessage {
    data class Scan(
        @JsonProperty("tabID") val tabId: String,
    ) : IncomingCodeScanMessage

    data class StartProjectScan(
        @JsonProperty("tabID") val tabId: String,
    ) : IncomingCodeScanMessage

    data class StartFileScan(
        @JsonProperty("tabID") val tabId: String,
    ) : IncomingCodeScanMessage

    data class StopProjectScan(
        @JsonProperty("tabID") val tabId: String,
    ) : IncomingCodeScanMessage

    data class StopFileScan(
        @JsonProperty("tabID") val tabId: String,
    ) : IncomingCodeScanMessage

    data class OpenIssuesPanel(
        @JsonProperty("tabID") val tabId: String,
    ) : IncomingCodeScanMessage

    data class TabCreated(
        @JsonProperty("tabID") val tabId: String,
    ) : IncomingCodeScanMessage

    data class Help(
        @JsonProperty("tabID") val tabId: String,
    ) : IncomingCodeScanMessage

    data class ClearChat(
        @JsonProperty("tabID") val tabId: String,
    ) : IncomingCodeScanMessage

    data class TabRemoved(
        @JsonProperty("tabID") val tabId: String,
    ) : IncomingCodeScanMessage

    data class ResponseBodyLinkClicked(
        @JsonProperty("tabID") val tabId: String,
        val link: String,
    ) : IncomingCodeScanMessage
}

// === App -> UI messages ===
sealed class CodeScanUiMessage(
    open val tabId: String?,
    open val type: String,
    open val messageId: String? = UUID.randomUUID().toString(),
) : CodeScanBaseMessage {
    val time = Instant.now().epochSecond
    val sender = CODE_SCAN_TAB_NAME
}

data class PromptProgressMessage(
    @JsonProperty("tabID") override val tabId: String,
    val progressField: ProgressField? = null,
) : CodeScanUiMessage(
    tabId = tabId,
    type = "updatePromptProgress",
)

data class ChatInputEnabledMessage(
    @JsonProperty("tabID") override val tabId: String,
    val enabled: Boolean,
) : CodeScanUiMessage(
    tabId = tabId,
    type = "chatInputEnabledMessage"
)

data class AuthenticationUpdateMessage(
    val authenticatingTabIDs: List<String>,
    val codeTransformEnabled: Boolean,
    val codeScanEnabled: Boolean,
    val message: String? = null,
) : CodeScanUiMessage(
    null,
    type = "authenticationUpdateMessage"
)

data class AuthenticationNeededExceptionMessage(
    @JsonProperty("tabID") override val tabId: String,
    val authType: AuthFollowUpType,
    val message: String? = null,
) : CodeScanUiMessage(
    tabId = tabId,
    type = "authNeededException"
)

data class CodeScanChatMessage(
    @JsonProperty("tabID") override val tabId: String,
    override val messageId: String? = UUID.randomUUID().toString(),
    val messageType: ChatMessageType,
    val message: String? = null,
    val buttons: List<Button>? = null,
    val formItems: List<FormItem>? = null,
    val followUps: List<FollowUp>? = null,
    val isLoading: Boolean = false,
    val canBeVoted: Boolean = true,
    val clearPreviousItemButtons: Boolean = true,
    val command: String? = null,
) : CodeScanUiMessage(
    messageId = messageId,
    tabId = tabId,
    type = "chatMessage",
)

data class UpdatePlaceholderMessage(
    @JsonProperty("tabID") override val tabId: String,
    val newPlaceholder: String,
) : CodeScanUiMessage(
    tabId = tabId,
    type = "updatePlaceholderMessage"
)

data class CodeScanChatMessageContent(
    val message: String? = null,
    val buttons: List<Button>? = null,
    val formItems: List<FormItem>? = null,
    val followUps: List<FollowUp>? = null,
    val type: ChatMessageType,
    val canBeVoted: Boolean = true,
)
