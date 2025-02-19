// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonqCodeTest.messages

import com.fasterxml.jackson.annotation.JsonProperty
import software.aws.toolkits.jetbrains.services.amazonq.auth.AuthFollowUpType
import software.aws.toolkits.jetbrains.services.amazonq.messages.AmazonQMessage
import software.aws.toolkits.jetbrains.services.cwc.messages.ChatMessageType
import software.aws.toolkits.jetbrains.services.cwc.messages.CodeReference
import software.aws.toolkits.jetbrains.services.cwc.messages.FollowUp
import java.time.Instant
import java.util.UUID

const val CODE_TEST_TAB_NAME = "codetest"

enum class CodeTestButtonId(val id: String) {
    StopTestGeneration("stop_test_generation"),
}

data class Button(
    val id: String,
    val text: String,
    val description: String? = null,
    val icon: String? = null,
    val keepCardAfterClick: Boolean? = false,
    val disabled: Boolean? = false,
    val waitMandatoryFormItems: Boolean? = false,
    val position: String = "inside",
    val status: String = "primary",
)

data class ProgressField(
    val title: String? = null,
    val value: Int? = null,
    val valueText: String? = null,
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

sealed interface CodeTestBaseMessage : AmazonQMessage

// === UI -> App Messages ===
sealed interface IncomingCodeTestMessage : CodeTestBaseMessage {
    data class ChatPrompt(
        val chatMessage: String,
        val command: String,
        @JsonProperty("tabID") val tabId: String,
    ) : IncomingCodeTestMessage

    data class StartTestGen(
        @JsonProperty("tabID") val tabId: String,
        val prompt: String,
    ) : IncomingCodeTestMessage

    data class ClickedLink(
        @JsonProperty("tabID") val tabId: String,
        val command: String,
        val messageId: String?,
        val link: String,
    ) : IncomingCodeTestMessage

    data class ClearChat(
        @JsonProperty("tabID") val tabId: String,
    ) : IncomingCodeTestMessage

    data class Help(
        @JsonProperty("tabID") val tabId: String,
    ) : IncomingCodeTestMessage

    data class NewTabCreated(
        @JsonProperty("tabID") val tabId: String,
    ) : IncomingCodeTestMessage

    data class TabRemoved(
        @JsonProperty("tabID") val tabId: String,
    ) : IncomingCodeTestMessage

    data class ButtonClicked(
        @JsonProperty("tabID") val tabId: String,
        @JsonProperty("actionID") val actionID: String,
    ) : IncomingCodeTestMessage

    data class ChatItemVoted(
        @JsonProperty("tabID") val tabId: String,
        val vote: String,
    ) : IncomingCodeTestMessage

    data class ChatItemFeedback(
        @JsonProperty("tabID") val tabId: String,
        val selectedOption: String,
        val comment: String?,
    ) : IncomingCodeTestMessage

    data class AuthFollowUpWasClicked(
        @JsonProperty("tabID") val tabId: String,
        val authType: AuthFollowUpType,
    ) : IncomingCodeTestMessage
}

data class UpdatePlaceholderMessage(
    @JsonProperty("tabID") override val tabId: String,
    val newPlaceholder: String,
) : UiMessage(
    tabId = tabId,
    type = "updatePlaceholderMessage"
)

data class ChatInputEnabledMessage(
    @JsonProperty("tabID") override val tabId: String,
    val enabled: Boolean,
) : UiMessage(
    tabId = tabId,
    type = "chatInputEnabledMessage"
)

data class CodeTestUpdateUIMessage(
    @JsonProperty("tabID") override val tabId: String,
    val loadingChat: Boolean?,
    val cancelButtonWhenLoading: Boolean?,
    val promptInputPlaceholder: String?,
    val promptInputDisabledState: Boolean?,
    val promptInputProgress: ProgressField?,
) : UiMessage(
    tabId = tabId,
    type = "updateUI"
)

// === App -> UI messages ===
sealed class UiMessage(
    open val tabId: String?,
    open val type: String,
    open val messageId: String? = UUID.randomUUID().toString(),
) : CodeTestBaseMessage {
    val time = Instant.now().epochSecond
    val sender = CODE_TEST_TAB_NAME
}

data class AuthenticationUpdateMessage(
    val authenticatingTabIDs: List<String>,
    val featureDevEnabled: Boolean,
    val codeTransformEnabled: Boolean,
    val codeScanEnabled: Boolean,
    val codeTestEnabled: Boolean,
    val docEnabled: Boolean,
    val message: String? = null,
) : UiMessage(
    null,
    type = "authenticationUpdateMessage"
)

data class AuthenticationNeededExceptionMessage(
    @JsonProperty("tabID") override val tabId: String,
    val authType: AuthFollowUpType,
    val message: String? = null,
) : UiMessage(
    tabId = tabId,
    type = "authNeededException"
)

data class PromptProgressMessage(
    @JsonProperty("tabID") override val tabId: String,
    val progressField: ProgressField? = null,
) : UiMessage(
    tabId = tabId,
    type = "updatePromptProgress",
)

data class CodeTestChatMessage(
    @JsonProperty("tabID") override val tabId: String,
    override val messageId: String? = UUID.randomUUID().toString(),
    val messageType: ChatMessageType,
    val message: String? = null,
    val buttons: List<Button>? = null,
    val canBeVoted: Boolean? = false,
    val formItems: List<FormItem>? = null,
    val followUps: List<FollowUp>? = null,
    val informationCard: Boolean? = false,
    val isAddingNewItem: Boolean = true,
    val isLoading: Boolean = false,
    val clearPreviousItemButtons: Boolean = true,
) : UiMessage(
    messageId = messageId,
    tabId = tabId,
    type = "chatMessage",
)

data class CodeTestUpdateAnswerMessage(
    @JsonProperty("tabID") override val tabId: String,
    override val messageId: String? = UUID.randomUUID().toString(),
    val messageType: ChatMessageType,
    val message: String? = null,
    val buttons: List<Button>? = null,
    val formItems: List<FormItem>? = null,
    val followUps: List<FollowUp>? = null,
    val isAddingNewItem: Boolean = true,
    val isLoading: Boolean = false,
    val clearPreviousItemButtons: Boolean = true,
    val fileList: List<String?>? = null,
    val footer: List<String>? = null,
    val projectRootName: String? = null,
    val codeReference: List<CodeReference>? = null,
) : UiMessage(
    messageId = messageId,
    tabId = tabId,
    type = "updateAnswer",
)

data class CodeTestAddAnswerMessage(
    @JsonProperty("tabID") override val tabId: String,
    override val messageId: String? = UUID.randomUUID().toString(),
    val messageType: ChatMessageType,
    val message: String? = null,
    val buttons: List<Button>? = null,
    val formItems: List<FormItem>? = null,
    val followUps: List<FollowUp>? = null,
    val isAddingNewItem: Boolean = true,
    val isLoading: Boolean = false,
    val clearPreviousItemButtons: Boolean = true,
    val fileList: List<String?>? = null,
    val footer: List<String>? = null,
    val projectRootName: String? = null,
    val canBeVoted: Boolean = false,
    val codeReference: List<CodeReference>? = null,
) : UiMessage(
    messageId = messageId,
    tabId = tabId,
    type = "addAnswer",
)

data class CodeTestChatMessageContent(
    val message: String? = null,
    val buttons: List<Button>? = null,
    val formItems: List<FormItem>? = null,
    val followUps: List<FollowUp>? = null,
    val type: ChatMessageType,
    val canBeVoted: Boolean = false,
    val informationCard: Boolean? = false,
    val fileList: List<String?>? = null,
    val footer: List<String>? = null,
    val projectRootName: String? = null,
    val codeReference: List<CodeReference>? = null,
)
