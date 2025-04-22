// Copyright 2023 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cwc.messages

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonValue
import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.JsonSerializer
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import software.amazon.awssdk.services.codewhispererstreaming.model.UserIntent
import software.aws.toolkits.jetbrains.services.amazonq.auth.AuthFollowUpType
import software.aws.toolkits.jetbrains.services.amazonq.messages.AmazonQMessage
import software.aws.toolkits.jetbrains.services.amazonq.onboarding.OnboardingPageInteractionType
import software.aws.toolkits.jetbrains.services.amazonq.util.HighlightCommand
import software.aws.toolkits.jetbrains.services.codewhisperer.util.DiagnosticDifferences
import software.aws.toolkits.jetbrains.services.cwc.clients.chat.model.FollowUpType
import java.time.Instant

sealed interface CwcMessage : AmazonQMessage

// === UI -> App Messages ===
sealed interface IncomingCwcMessage : CwcMessage {
    interface TabId {
        val tabId: String?
    }

    interface MessageId {
        val messageId: String?
    }

    data class ClearChat(
        @JsonProperty("tabID") override val tabId: String,
    ) : IncomingCwcMessage, TabId

    data class Help(
        @JsonProperty("tabID") override val tabId: String,
    ) : IncomingCwcMessage, TabId

    data class ChatPrompt(
        val chatMessage: String,
        val command: String,
        @JsonProperty("tabID") override val tabId: String,
        val userIntent: String?,
    ) : IncomingCwcMessage, TabId

    data class TabAdded(
        @JsonProperty("tabID") override val tabId: String,
        val tabType: String,
    ) : IncomingCwcMessage, TabId

    data class TabRemoved(
        @JsonProperty("tabID") override val tabId: String,
        val tabType: String,
    ) : IncomingCwcMessage, TabId

    data class TabChanged(
        @JsonProperty("tabID") override val tabId: String,
        @JsonProperty("prevTabID") val prevTabId: String?,
    ) : IncomingCwcMessage, TabId

    data class FollowupClicked(
        val followUp: FollowUp,
        @JsonProperty("tabID") override val tabId: String,
        override val messageId: String?,
        val command: String,
        val tabType: String,
    ) : IncomingCwcMessage, TabId, MessageId

    data class CopyCodeToClipboard(
        val command: String?,
        @JsonProperty("tabID") override val tabId: String,
        override val messageId: String,
        val userIntent: UserIntent?,
        val code: String,
        val insertionTargetType: String?,
        val eventId: String?,
        val codeBlockIndex: Int?,
        val totalCodeBlocks: Int?,
        val codeBlockLanguage: String?,
    ) : IncomingCwcMessage, TabId, MessageId

    data class InsertCodeAtCursorPosition(
        @JsonProperty("tabID") override val tabId: String,
        override val messageId: String,
        val userIntent: UserIntent?,
        val code: String,
        val insertionTargetType: String?,
        val codeReference: List<CodeReference>?,
        val eventId: String?,
        val codeBlockIndex: Int?,
        val totalCodeBlocks: Int?,
        val codeBlockLanguage: String?,
        var diagnosticsDifferences: DiagnosticDifferences?,
    ) : IncomingCwcMessage, TabId, MessageId

    data class TriggerTabIdReceived(
        @JsonProperty("triggerID") val triggerId: String,
        @JsonProperty("tabID") override val tabId: String,
    ) : IncomingCwcMessage, TabId

    data class StopResponse(
        @JsonProperty("tabID") override val tabId: String,
    ) : IncomingCwcMessage, TabId

    data class ChatItemVoted(
        @JsonProperty("tabID") override val tabId: String,
        override val messageId: String,
        val vote: String, // upvote / downvote
    ) : IncomingCwcMessage, TabId, MessageId

    data class ChatItemFeedback(
        @JsonProperty("tabID") override val tabId: String,
        val selectedOption: String,
        val comment: String?,
        override val messageId: String,
    ) : IncomingCwcMessage, TabId, MessageId

    data class UIFocus(
        val command: String,
        @JsonDeserialize(using = FocusTypeDeserializer::class)
        @JsonSerialize(using = FocusTypeSerializer::class)
        val type: FocusType,
    ) : IncomingCwcMessage

    data class OpenUserGuide(
        val userGuideLink: String,
    ) : IncomingCwcMessage

    data class ClickedLink(
        @JsonProperty("command") val type: LinkType,
        @JsonProperty("tabID") override val tabId: String,
        override val messageId: String?,
        val link: String,
    ) : IncomingCwcMessage, TabId, MessageId

    data class AuthFollowUpWasClicked(
        @JsonProperty("tabID") override val tabId: String,
        val authType: AuthFollowUpType,
    ) : IncomingCwcMessage, TabId

    data class OpenSettings(
        @JsonProperty("tabID") override val tabId: String? = null,
    ) : IncomingCwcMessage, TabId
}

enum class FocusType {
    FOCUS,
    BLUR,
}

enum class LinkType(
    @field:JsonValue val command: String,
) {
    SourceLink("source-link-click"),
    BodyLink("response-body-link-click"),
    FooterInfoLink("footer-info-link-click"),
}

class FocusTypeDeserializer : JsonDeserializer<FocusType>() {
    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): FocusType = FocusType.valueOf(p.valueAsString.uppercase())
}

class FocusTypeSerializer : JsonSerializer<FocusType>() {
    override fun serialize(value: FocusType, gen: JsonGenerator, serializers: SerializerProvider) {
        gen.writeString(value.name.lowercase())
    }
}

sealed class UiMessage(
    open val tabId: String?,
    open val type: String,
) : CwcMessage {
    val time = Instant.now().epochSecond
    val sender = "CWChat"
}

enum class ChatMessageType(
    @field:JsonValue val json: String,
) {
    AnswerStream("answer-stream"),
    AnswerPart("answer-part"),
    Answer("answer"),
    AIPrompt("ai-prompt"),
    Prompt("prompt"),
}

data class CodeReference(
    val licenseName: String? = null,
    val repository: String? = null,
    val url: String? = null,
    val recommendationContentSpan: RecommendationContentSpan? = null,
    val information: String,
)

data class RecommendationContentSpan(
    val start: Int,
    val end: Int,
)

data class FollowUp(
    val type: FollowUpType,
    val pillText: String,
    val prompt: String,
    val status: String? = null,
)

data class Suggestion(
    val title: String,
    val url: String,
    val body: String,
    val id: Int,
    val type: String?,
    val context: List<String>,
)

// === App -> UI messages ===

data class ChatMessage(
    @JsonProperty("tabID") override val tabId: String,
    @JsonProperty("triggerID") val triggerId: String,
    val messageType: ChatMessageType,
    val messageId: String,
    val message: String? = null,
    val followUps: List<FollowUp>? = null,
    val followUpsHeader: String? = null,
    val relatedSuggestions: List<Suggestion>? = null,
    val codeReference: List<CodeReference>? = null,
    val userIntent: UserIntent? = null,
    val codeBlockLanguage: String? = "plaintext",
) : UiMessage(
    tabId = tabId,
    type = "chatMessage",
)

data class EditorContextCommandMessage(
    val message: String?,
    @JsonProperty("triggerID") val triggerId: String?,
    val command: String?,
) : UiMessage(
    tabId = null,
    type = "editorContextCommandMessage",
)

data class AuthNeededException(
    @JsonProperty("tabID") override val tabId: String,
    @JsonProperty("triggerID") val triggerId: String,
    val authType: AuthFollowUpType,
    val message: String,
) : UiMessage(
    tabId = tabId,
    type = "authNeededException",
)

data class ErrorMessage(
    @JsonProperty("tabID") override val tabId: String,
    val title: String,
    val message: String,
    val messageId: String?,
) : UiMessage(
    tabId = tabId,
    type = "errorMessage",
)

data class FeatureConfigsAvailableMessage(
    val highlightCommand: HighlightCommand?,

) : UiMessage(
    null,
    type = "featureConfigsAvailableMessage",
)

data class QuickActionMessage(
    val message: String,
    @JsonProperty("triggerID") val triggerId: String,
) : UiMessage(
    tabId = null,
    type = "editorContextCommandMessage",
)

data class OnboardingPageInteractionMessage(
    val message: String,
    val interactionType: OnboardingPageInteractionType,
    @JsonProperty("triggerID") val triggerId: String,
) : UiMessage(
    tabId = null,
    type = "editorContextCommandMessage",
)

data class OpenSettingsMessage(
    @JsonProperty("tabID") override val tabId: String,
) : UiMessage(
    tabId = tabId,
    type = "openSettingsMessage",
)
