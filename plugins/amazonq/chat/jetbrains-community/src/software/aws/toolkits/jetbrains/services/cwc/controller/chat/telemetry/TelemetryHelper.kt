// Copyright 2023 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cwc.controller.chat.telemetry

import com.intellij.openapi.project.Project
import org.jetbrains.annotations.VisibleForTesting
import software.amazon.awssdk.services.codewhispererruntime.model.ChatInteractWithMessageEvent
import software.amazon.awssdk.services.codewhispererruntime.model.ChatMessageInteractionType
import software.amazon.awssdk.services.codewhispererruntime.model.InlineChatUserDecision
import software.amazon.awssdk.services.codewhispererstreaming.model.UserIntent
import software.amazon.awssdk.services.toolkittelemetry.model.Sentiment
import software.aws.toolkits.core.utils.debug
import software.aws.toolkits.core.utils.getLogger
import software.aws.toolkits.core.utils.info
import software.aws.toolkits.core.utils.warn
import software.aws.toolkits.jetbrains.core.credentials.ToolkitConnection
import software.aws.toolkits.jetbrains.core.credentials.ToolkitConnectionManager
import software.aws.toolkits.jetbrains.core.credentials.pinning.QConnection
import software.aws.toolkits.jetbrains.services.codewhisperer.credentials.CodeWhispererClientAdaptor
import software.aws.toolkits.jetbrains.services.codewhisperer.customization.CodeWhispererCustomization
import software.aws.toolkits.jetbrains.services.codewhisperer.customization.CodeWhispererModelConfigurator
import software.aws.toolkits.jetbrains.services.cwc.clients.chat.model.ChatRequestData
import software.aws.toolkits.jetbrains.services.cwc.clients.chat.model.TriggerType
import software.aws.toolkits.jetbrains.services.cwc.clients.chat.v1.ChatSessionV1
import software.aws.toolkits.jetbrains.services.cwc.controller.ChatController
import software.aws.toolkits.jetbrains.services.cwc.messages.ChatMessage
import software.aws.toolkits.jetbrains.services.cwc.messages.IncomingCwcMessage
import software.aws.toolkits.jetbrains.services.cwc.messages.LinkType
import software.aws.toolkits.jetbrains.services.cwc.storage.ChatSessionStorage
import software.aws.toolkits.jetbrains.services.telemetry.TelemetryService
import software.aws.toolkits.jetbrains.settings.CodeWhispererSettings
import software.aws.toolkits.jetbrains.utils.notifyError
import software.aws.toolkits.resources.message
import software.aws.toolkits.telemetry.AuthTelemetry
import software.aws.toolkits.telemetry.CwsprChatCommandType
import software.aws.toolkits.telemetry.CwsprChatConversationType
import software.aws.toolkits.telemetry.CwsprChatInteractionType
import software.aws.toolkits.telemetry.CwsprChatTriggerInteraction
import software.aws.toolkits.telemetry.CwsprChatUserIntent
import software.aws.toolkits.telemetry.Telemetry
import java.time.Duration
import java.time.Instant
import software.amazon.awssdk.services.codewhispererruntime.model.UserIntent as CWClientUserIntent
import software.aws.toolkits.jetbrains.services.codewhisperer.util.getDocumentDiagnostics

class TelemetryHelper(private val project: Project, private val sessionStorage: ChatSessionStorage) {
    private val responseStreamStartTime: MutableMap<String, Instant> = mutableMapOf()
    private val responseStreamTotalTime: MutableMap<String, Int> = mutableMapOf()
    private val responseStreamTimeForChunks: MutableMap<String, MutableList<Instant>> = mutableMapOf()
    private val responseHasProjectContext: MutableMap<String, Boolean> = mutableMapOf()

    private val customization: CodeWhispererCustomization?
        get() = CodeWhispererModelConfigurator.getInstance().activeCustomization(project)

    fun getConversationId(tabId: String): String? = sessionStorage.getSession(tabId)?.session?.conversationId

    private fun getTelemetryUserIntent(userIntent: UserIntent): CwsprChatUserIntent = when (userIntent) {
        UserIntent.SUGGEST_ALTERNATE_IMPLEMENTATION -> CwsprChatUserIntent.SuggestAlternateImplementation
        UserIntent.APPLY_COMMON_BEST_PRACTICES -> CwsprChatUserIntent.ApplyCommonBestPractices
        UserIntent.IMPROVE_CODE -> CwsprChatUserIntent.ImproveCode
        UserIntent.SHOW_EXAMPLES -> CwsprChatUserIntent.ShowExample
        UserIntent.CITE_SOURCES -> CwsprChatUserIntent.CiteSources
        UserIntent.EXPLAIN_LINE_BY_LINE -> CwsprChatUserIntent.ExplainLineByLine
        UserIntent.EXPLAIN_CODE_SELECTION -> CwsprChatUserIntent.ExplainCodeSelection
        UserIntent.GENERATE_UNIT_TESTS -> CwsprChatUserIntent.GenerateUnitTests
        UserIntent.UNKNOWN_TO_SDK_VERSION -> CwsprChatUserIntent.Unknown
    }

    private fun getTelemetryTriggerType(triggerType: TriggerType): CwsprChatTriggerInteraction = when (triggerType) {
        TriggerType.Click, TriggerType.CodeScanButton, TriggerType.Inline -> CwsprChatTriggerInteraction.Click
        TriggerType.ContextMenu, TriggerType.Hotkeys -> CwsprChatTriggerInteraction.ContextMenu
    }

    fun getIsProjectContextEnabled() = CodeWhispererSettings.getInstance().isProjectContextEnabled()

    // When chat panel is focused
    fun recordEnterFocusChat() {
        Telemetry.amazonq.enterFocusChat.use { it.passive(true) }
    }

    // When chat panel is unfocused
    fun recordExitFocusChat() {
        Telemetry.amazonq.exitFocusChat.use { it.passive(true) }
    }

    fun recordStartConversation(tabId: String, data: ChatRequestData) {
        val sessionHistory = sessionStorage.getSession(tabId)?.history ?: return
        if (sessionHistory.size > 1) return

        Telemetry.amazonq.startConversation.use { span ->
            span.cwsprChatConversationId(getConversationId(tabId).orEmpty())
                .cwsprChatTriggerInteraction(getTelemetryTriggerType(data.triggerType))
                .cwsprChatConversationType(CwsprChatConversationType.Chat)
                .cwsprChatUserIntent(data.userIntent?.let { getTelemetryUserIntent(it) })
                .cwsprChatHasCodeSnippet(data.activeFileContext.focusAreaContext?.codeSelection?.isNotEmpty() == true)
                .cwsprChatProgrammingLanguage(data.activeFileContext.fileContext?.fileLanguage)
                .credentialStartUrl(getStartUrl(project))
                .cwsprChatHasProjectContext(getIsProjectContextEnabled() && data.useRelevantDocuments && data.relevantTextDocuments.isNotEmpty())
        }
    }

    // When Chat API responds to a user message (full response streamed)
    fun recordAddMessage(data: ChatRequestData, response: ChatMessage, responseLength: Int, statusCode: Int, numberOfCodeBlocks: Int) {
        Telemetry.amazonq.addMessage.use { span ->
            span.cwsprChatConversationId(getConversationId(response.tabId).orEmpty())
                .cwsprChatMessageId(response.messageId)
                .cwsprChatTriggerInteraction(getTelemetryTriggerType(data.triggerType))
                .cwsprChatUserIntent(data.userIntent?.let { getTelemetryUserIntent(it) })
                .cwsprChatHasCodeSnippet(data.activeFileContext.focusAreaContext?.codeSelection?.isNotEmpty() ?: false)
                .cwsprChatProgrammingLanguage(data.activeFileContext.fileContext?.fileLanguage)
                .cwsprChatActiveEditorTotalCharacters(data.activeFileContext.focusAreaContext?.codeSelection?.length?.toLong())
                .cwsprChatActiveEditorImportCount(data.activeFileContext.focusAreaContext?.codeNames?.fullyQualifiedNames?.used?.size?.toLong())
                .cwsprChatResponseCodeSnippetCount(numberOfCodeBlocks.toLong())
                .cwsprChatResponseCode(statusCode.toLong())
                .cwsprChatSourceLinkCount(response.relatedSuggestions?.size?.toLong())
                .cwsprChatReferencesCount(0L) // TODO
                .cwsprChatFollowUpCount(response.followUps?.size?.toLong())
                .cwsprChatTimeToFirstChunk(getResponseStreamTimeToFirstChunk(response.tabId).toLong())
                .cwsprChatTimeBetweenChunks("[${getResponseStreamTimeBetweenChunks(response.tabId).joinToString(")")}]")
                .cwsprChatFullResponseLatency(responseStreamTotalTime[response.tabId]?.toLong() ?: 0)
                .cwsprChatRequestLength(data.message.length.toLong())
                .cwsprChatResponseLength(responseLength.toLong())
                .cwsprChatConversationType(CwsprChatConversationType.Chat)
                .credentialStartUrl(getStartUrl(project))
                .codewhispererCustomizationArn(data.customization?.arn)
                .cwsprChatHasProjectContext(getMessageHasProjectContext(response.messageId))
        }

        val programmingLanguage = data.activeFileContext.fileContext?.fileLanguage
        val validProgrammingLanguage = if (ChatSessionV1.validLanguages.contains(programmingLanguage)) programmingLanguage else null

        CodeWhispererClientAdaptor.getInstance(project).sendChatAddMessageTelemetry(
            getConversationId(response.tabId).orEmpty(),
            response.messageId,
            CWClientUserIntent.fromValue(data.userIntent?.name),
            (data.activeFileContext.focusAreaContext?.codeSelection?.isNotEmpty() ?: false),
            validProgrammingLanguage,
            data.activeFileContext.focusAreaContext?.codeSelection?.length,
            getResponseStreamTimeToFirstChunk(response.tabId),
            getResponseStreamTimeBetweenChunks(response.tabId),
            (responseStreamTotalTime[response.tabId] ?: 0).toDouble(),
            data.message.length,
            responseLength,
            numberOfCodeBlocks,
            getMessageHasProjectContext(response.messageId),
            data.customization
        )
    }

    fun recordInlineChatTelemetry(
        requestId: String,
        inputLength: Int?,
        numSelectedLines: Int?,
        codeIntent: Boolean?,
        userDecision: InlineChatUserDecision?,
        responseStartLatency: Double?,
        responseEndLatency: Double?,
        numSuggestionAddChars: Int?,
        numSuggestionAddLines: Int?,
        numSuggestionDelChars: Int?,
        numSuggestionDelLines: Int?,
        programmingLanguage: String?,
    ) {
        CodeWhispererClientAdaptor.getInstance(project).sendInlineChatTelemetry(
            requestId, inputLength, numSelectedLines, codeIntent, userDecision,
            responseStartLatency, responseEndLatency, numSuggestionAddChars, numSuggestionAddLines, numSuggestionDelChars, numSuggestionDelLines,
            programmingLanguage
        )
    }

    fun recordMessageResponseError(data: ChatRequestData, tabId: String, responseCode: Int) {
        Telemetry.amazonq.messageResponseError.use { span ->
            span.cwsprChatConversationId(getConversationId(tabId).orEmpty())
                .cwsprChatTriggerInteraction(getTelemetryTriggerType(data.triggerType))
                .cwsprChatUserIntent(data.userIntent?.let { getTelemetryUserIntent(it) })
                .cwsprChatHasCodeSnippet(data.activeFileContext.focusAreaContext?.codeSelection?.isNotEmpty() ?: false)
                .cwsprChatProgrammingLanguage(data.activeFileContext.fileContext?.fileLanguage)
                .cwsprChatActiveEditorTotalCharacters(data.activeFileContext.focusAreaContext?.codeSelection?.length?.toLong())
                .cwsprChatActiveEditorImportCount(data.activeFileContext.focusAreaContext?.codeNames?.fullyQualifiedNames?.used?.size?.toLong())
                .cwsprChatResponseCode(responseCode.toLong())
                .cwsprChatRequestLength(data.message.length.toLong())
                .cwsprChatConversationType(CwsprChatConversationType.Chat)
                .credentialStartUrl(getStartUrl(project))
        }
    }

    // When user interacts with a message (e.g. copy code, insert code, vote)
    suspend fun recordInteractWithMessage(message: IncomingCwcMessage) = Telemetry.amazonq.interactWithMessage.use { span ->
        if (message is IncomingCwcMessage.TabId) {
            span.cwsprChatConversationId(message.tabId?.let { getConversationId(it) }.orEmpty())
        }

        if (message is IncomingCwcMessage.MessageId) {
            span.cwsprChatMessageId(message.messageId.orEmpty())
                .cwsprChatHasProjectContext(message.messageId?.let { getMessageHasProjectContext(it) })
        }

        span.credentialStartUrl(getStartUrl(project))

        val event: ChatInteractWithMessageEvent? = when (message) {
            is IncomingCwcMessage.ChatItemVoted -> {
                span.cwsprChatInteractionType(
                    when (message.vote) {
                        "upvote" -> CwsprChatInteractionType.Upvote
                        "downvote" -> CwsprChatInteractionType.Downvote
                        else -> CwsprChatInteractionType.Unknown
                    }
                )
                ChatInteractWithMessageEvent.builder().apply {
                    conversationId(getConversationId(message.tabId).orEmpty())
                    messageId(message.messageId)
                    interactionType(
                        when (message.vote) {
                            "upvote" -> ChatMessageInteractionType.UPVOTE
                            "downvote" -> ChatMessageInteractionType.DOWNVOTE
                            else -> ChatMessageInteractionType.UNKNOWN_TO_SDK_VERSION
                        }
                    )
                    hasProjectLevelContext(getMessageHasProjectContext(message.messageId))
                }.build()
            }

            is IncomingCwcMessage.FollowupClicked -> {
                span.cwsprChatInteractionType(CwsprChatInteractionType.ClickFollowUp)

                ChatInteractWithMessageEvent.builder().apply {
                    conversationId(getConversationId(message.tabId).orEmpty())
                    messageId(message.messageId.orEmpty())
                    interactionType(ChatMessageInteractionType.CLICK_FOLLOW_UP)
                    hasProjectLevelContext(getMessageHasProjectContext(message.messageId.orEmpty()))
                }.build()
            }

            is IncomingCwcMessage.CopyCodeToClipboard -> {
                span.cwsprChatUserIntent(message.userIntent?.let { getTelemetryUserIntent(it) })
                    .cwsprChatInteractionType(CwsprChatInteractionType.CopySnippet)
                    .cwsprChatAcceptedCharactersLength(message.code.length)
                    .cwsprChatInteractionTarget(message.insertionTargetType)
                    .cwsprChatCodeBlockIndex(message.codeBlockIndex)
                    .cwsprChatTotalCodeBlocks(message.totalCodeBlocks)
                    .cwsprChatProgrammingLanguage(message.codeBlockLanguage)

                ChatInteractWithMessageEvent.builder().apply {
                    conversationId(getConversationId(message.tabId).orEmpty())
                    messageId(message.messageId)
                    interactionType(ChatMessageInteractionType.COPY_SNIPPET)
                    interactionTarget(message.insertionTargetType)
                    acceptedCharacterCount(message.code.length)
                    hasProjectLevelContext(getMessageHasProjectContext(message.messageId))
                }.build()
            }

            is IncomingCwcMessage.InsertCodeAtCursorPosition -> {
                span.cwsprChatUserIntent(message.userIntent?.let { getTelemetryUserIntent(it) })
                    .cwsprChatInteractionType(CwsprChatInteractionType.InsertAtCursor)
                    .cwsprChatAcceptedCharactersLength(message.code.length)
                    .cwsprChatAcceptedNumberOfLines(message.code.lines().size)
                    .cwsprChatInteractionTarget(message.insertionTargetType)
                    .cwsprChatCodeBlockIndex(message.codeBlockIndex)
                    .cwsprChatTotalCodeBlocks(message.totalCodeBlocks)
                    .cwsprChatProgrammingLanguage(message.codeBlockLanguage)

                ChatInteractWithMessageEvent.builder().apply {
                    conversationId(getConversationId(message.tabId).orEmpty())
                    messageId(message.messageId)
                    interactionType(ChatMessageInteractionType.INSERT_AT_CURSOR)
                    interactionTarget(message.insertionTargetType)
                    acceptedCharacterCount(message.code.length)
                    acceptedLineCount(message.code.lines().size)
                    hasProjectLevelContext(getMessageHasProjectContext(message.messageId))
                    addedIdeDiagnostics(message.diagnosticsDifferences?.added)
                    removedIdeDiagnostics(message.diagnosticsDifferences?.removed)
                }.build()
            }

            is IncomingCwcMessage.ClickedLink -> {
                // Null when internal Amazon link is clicked
                if (message.messageId == null) return null

                val linkInteractionType = when (message.type) {
                    LinkType.SourceLink -> CwsprChatInteractionType.ClickLink
                    LinkType.BodyLink -> CwsprChatInteractionType.ClickBodyLink
                    else -> CwsprChatInteractionType.Unknown
                }
                span.cwsprChatInteractionType(linkInteractionType)
                span.cwsprChatInteractionTarget(message.link)

                ChatInteractWithMessageEvent.builder().apply {
                    conversationId(getConversationId(message.tabId).orEmpty())
                    messageId(message.messageId)
                    interactionType(
                        when (message.type) {
                            LinkType.SourceLink -> ChatMessageInteractionType.CLICK_LINK
                            LinkType.BodyLink -> ChatMessageInteractionType.CLICK_BODY_LINK
                            else -> ChatMessageInteractionType.UNKNOWN_TO_SDK_VERSION
                        }
                    )
                    interactionTarget(message.link)
                    hasProjectLevelContext(getMessageHasProjectContext(message.messageId))
                }.build()
            }

            is IncomingCwcMessage.ChatItemFeedback -> {
                span.cwsprChatInteractionType(CwsprChatInteractionType.Unknown)
                recordFeedback(message)
                null
            }

            else -> {
                span.cwsprChatInteractionType(CwsprChatInteractionType.Unknown)

                null
            }
        }?.let {
            // override request and add customizationArn if it's not null, else return itself
            customization?.let { myCustomization ->
                it.toBuilder()
                    .customizationArn(myCustomization.arn)
                    .build()
            } ?: it
        }

        event?.let {
            val steResponse = CodeWhispererClientAdaptor.getInstance(project).sendChatInteractWithMessageTelemetry(it)
            logger.debug {
                "Successfully sendTelemetryEvent for ChatInteractWithMessage with requestId=${steResponse.responseMetadata().requestId()}"
            }
        }
    }

    private suspend fun recordFeedback(message: IncomingCwcMessage.ChatItemFeedback) {
        val comment = FeedbackComment(
            conversationId = getConversationId(message.tabId).orEmpty(),
            messageId = message.messageId,
            reason = message.selectedOption,
            userComment = message.comment.orEmpty(),
        )

        try {
            TelemetryService.getInstance().sendFeedback(
                sentiment = Sentiment.NEGATIVE,
                comment = ChatController.objectMapper.writeValueAsString(comment),
            )
            logger.info { "CodeWhispererChat answer feedback sent: \"Negative\"" }
            recordFeedbackResult(true)
        } catch (e: Throwable) {
            e.notifyError(message("feedback.submit_failed", e))
            logger.warn(e) { "Failed to submit feedback" }
            recordFeedbackResult(false)
            return
        }
    }

    private fun recordFeedbackResult(success: Boolean) {
        Telemetry.feedback.result.use { it.success(success) }
    }

    // When a conversation(tab) is focused
    fun recordEnterFocusConversation(tabId: String) {
        getConversationId(tabId)?.let {
            Telemetry.amazonq.enterFocusConversation.use { span ->
                span.cwsprChatConversationId(it)
            }
        }
    }

    // When a conversation(tab) is unfocused
    fun recordExitFocusConversation(tabId: String) {
        getConversationId(tabId)?.let {
            Telemetry.amazonq.exitFocusConversation.use { span ->
                span.cwsprChatConversationId(it)
            }
        }
    }

    fun setResponseStreamStartTime(tabId: String) {
        responseStreamStartTime[tabId] = Instant.now()
        responseStreamTimeForChunks[tabId] = mutableListOf(Instant.now())
    }

    fun setResponseStreamTimeForChunks(tabId: String) {
        val chunkTimes = responseStreamTimeForChunks.getOrPut(tabId) { mutableListOf() }
        chunkTimes += Instant.now()
    }

    fun setResponseStreamTotalTime(tabId: String) {
        val totalTime = Duration.between(responseStreamStartTime[tabId], Instant.now()).toMillis().toInt()
        responseStreamTotalTime[tabId] = totalTime
    }

    fun setResponseHasProjectContext(messageId: String, hasProjectContext: Boolean) {
        responseHasProjectContext[messageId] = hasProjectContext
    }

    private fun getMessageHasProjectContext(messageId: String) = responseHasProjectContext.getOrDefault(messageId, false)

    @VisibleForTesting
    fun getResponseStreamTimeToFirstChunk(tabId: String): Double {
        val chunkTimes = responseStreamTimeForChunks[tabId] ?: return 0.0
        if (chunkTimes.size == 1) return Duration.between(chunkTimes[0], Instant.now()).toMillis().toDouble()
        return Duration.between(chunkTimes[0], chunkTimes[1]).toMillis().toDouble()
    }

    @VisibleForTesting
    fun getResponseStreamTimeBetweenChunks(tabId: String): List<Double> = try {
        val chunkDeltaTimes = mutableListOf<Double>()
        val chunkTimes = responseStreamTimeForChunks[tabId] ?: listOf(Instant.now())
        for (idx in 0 until (chunkTimes.size - 1)) {
            chunkDeltaTimes += Duration.between(chunkTimes[idx], chunkTimes[idx + 1]).toMillis().toDouble()
        }
        chunkDeltaTimes.take(100)
    } catch (e: Exception) {
        listOf(-1.0)
    }

    companion object {
        private val logger = getLogger<TelemetryHelper>()

        private fun getQConnection(project: Project): ToolkitConnection? = ToolkitConnectionManager.getInstance(
            project
        ).activeConnectionForFeature(QConnection.getInstance())

        fun recordOpenChat(project: Project) {
            Telemetry.amazonq.openChat.use { it.passive(true) }
            if (getQConnection(project) == null) {
                AuthTelemetry.signInPageOpened()
            }
        }

        fun recordCloseChat(project: Project) {
            Telemetry.amazonq.closeChat.use { it.passive(true) }
            if (getQConnection(project) == null) {
                AuthTelemetry.signInPageClosed()
            }
        }

        fun recordTelemetryChatRunCommand(type: CwsprChatCommandType, name: String? = null, startUrl: String? = null) {
            Telemetry.amazonq.runCommand.use {
                it.cwsprChatCommandType(type)
                    .cwsprChatCommandName(name)
                    .credentialStartUrl(startUrl)
            }
        }
    }
}

data class FeedbackComment(
    val conversationId: String,
    val messageId: String?,
    val reason: String,
    val userComment: String,
    val type: String = "codewhisperer-chat-answer-feedback",
)
