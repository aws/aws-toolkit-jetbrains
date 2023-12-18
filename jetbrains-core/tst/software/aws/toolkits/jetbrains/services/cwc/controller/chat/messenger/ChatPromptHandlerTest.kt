// Copyright 2023 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cwc.controller.chat.messenger

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Test
import software.amazon.awssdk.services.codewhispererstreaming.model.CodeWhispererStreamingException
import software.aws.toolkits.jetbrains.services.cwc.clients.chat.ChatSession
import software.aws.toolkits.jetbrains.services.cwc.clients.chat.exceptions.ChatApiException
import software.aws.toolkits.jetbrains.services.cwc.clients.chat.model.ChatRequestData
import software.aws.toolkits.jetbrains.services.cwc.clients.chat.model.ChatResponseEvent
import software.aws.toolkits.jetbrains.services.cwc.clients.chat.model.FollowUpType
import software.aws.toolkits.jetbrains.services.cwc.clients.chat.model.RecommendationContentSpan
import software.aws.toolkits.jetbrains.services.cwc.clients.chat.model.Reference
import software.aws.toolkits.jetbrains.services.cwc.clients.chat.model.SuggestedFollowUp
import software.aws.toolkits.jetbrains.services.cwc.clients.chat.model.Suggestion
import software.aws.toolkits.jetbrains.services.cwc.controller.chat.telemetry.TelemetryHelper
import software.aws.toolkits.jetbrains.services.cwc.messages.ChatMessage
import software.aws.toolkits.jetbrains.services.cwc.messages.ChatMessageType
import software.aws.toolkits.jetbrains.services.cwc.messages.CodeReference
import software.aws.toolkits.jetbrains.services.cwc.messages.CwcMessage
import software.aws.toolkits.jetbrains.services.cwc.messages.FollowUp
import software.aws.toolkits.jetbrains.services.cwc.storage.ChatSessionInfo


class ChatPromptHandlerTest {

// Write unit tests for ChatPromptHandler.kt

// handle

// 1:
// Mock sessionInfo.session.chat(any()) to return a flow
// We will send events to this flow to invoke desired test behavior

// Note: Testing is not dependent on input data
// But on events returned from the session.chat flow object to our created flow

// ?? How to listen to flow of ChatPromptHandler?

// processChatEvent will be tested by checking emitted ChatMessage

// SUMMARY: ALL Verification is done via the ChatMessage(s) returned

    private val mockTelemetryHelper = mockk<TelemetryHelper>(relaxed = true)
    private val mockChatSessionInfo = mockk<ChatSessionInfo>(relaxed = true)
    private val mockChatSession = mockk<ChatSession>(relaxed = true)

    private lateinit var testChatFlow: Flow<ChatResponseEvent>

    private lateinit var chatPromptHandler: ChatPromptHandler

    private val testTabId = "testTabId"
    private val testData = mockk<ChatRequestData>(relaxed = true)
    private val testTriggerId = "testTriggerId"
    private val testRequestId = "testRequestId"

    @Before
    fun setup() {
        chatPromptHandler = ChatPromptHandler.create(mockTelemetryHelper)

        every { mockChatSessionInfo.session } returns mockChatSession
    }

    @Test
    fun `Empty single ChatResponse handled`() {
        // === Arrange ===
        testChatFlow = flow {
            emit(
                ChatResponseEvent(
                    requestId = testRequestId,
                    statusCode = 200,
                    token = null,
                    followUps = null,
                    suggestions = null,
                    codeReferences = null,
                    query = null
                )
            )
        }
        every { mockChatSession.chat(any()) } returns testChatFlow

        // === Act ===
        val chatFlow = chatPromptHandler.handle(
            tabId = testTabId,
            triggerId = testTriggerId,
            data = testData,
            sessionInfo = mockChatSessionInfo
        )

        // === Assert ===
        val expectedSequence = listOf(
            // Start message
            ChatMessage(
                tabId = testTabId,
                triggerId = testTriggerId,
                messageId = "",
                messageType = ChatMessageType.AnswerStream,
                message = ""
            ),
            // Completion message
            ChatMessage(
                tabId = testTabId,
                triggerId = testTriggerId,
                messageId = testRequestId,
                messageType = ChatMessageType.Answer,
                followUps = listOf(),
                message = null
            ),
        )

        verifyChatFlow(expectedSequence, chatFlow)

        // telemetry
        verify(exactly = 1) {
            mockTelemetryHelper.setResponseStreamStartTime(testTabId)
        }
        verify(exactly = 1) {
            mockTelemetryHelper.setResponseStreamTotalTime(testTabId)
        }
        verify(exactly = 1) {
            mockTelemetryHelper.recordAddMessage(testData, expectedSequence[1], 0, 200)
        }
    }

    @Test
    fun `Sequential ChatResponses with relatedSuggestions handled`() {
        // === Arrange ===
        val numSuggestionsPerEvent = 2;
        val numSuggestions = 4

        // Autogenerate incremental suggestion
        val suggestions = mutableListOf<Suggestion>()

        for (i in 0 until numSuggestions) {
            val suggestion = Suggestion(
                title = "title$i",
                url = "url$i",
                body = "body$i",
                type = "type$i",
                context = listOf("context$i"),
                metadata = null,
            )
            suggestions.add(suggestion)
        }

        val token1 = "token1"
        val token2 = "token2"

        testChatFlow = flow {
            emit(
                ChatResponseEvent(
                    requestId = testRequestId,
                    statusCode = 200,
                    token = token1,
                    followUps = null,
                    suggestions = suggestions.subList(0, 2),
                    codeReferences = null,
                    query = null
                ),
            )
            emit(
                ChatResponseEvent(
                    requestId = testRequestId,
                    statusCode = 200,
                    token = token2,
                    followUps = null,
                    suggestions = suggestions.subList(2, 4),
                    codeReferences = null,
                    query = null
                )
            )
        }
        every { mockChatSession.chat(any()) } returns testChatFlow

        // === Act ===
        val chatFlow = chatPromptHandler.handle(
            tabId = testTabId,
            triggerId = testTriggerId,
            data = testData,
            sessionInfo = mockChatSessionInfo
        )

        // === Assert ===
        val expectedSuggestions = mutableListOf<software.aws.toolkits.jetbrains.services.cwc.messages.Suggestion>();
        for (i in 0 until numSuggestions) {
            val suggestion = software.aws.toolkits.jetbrains.services.cwc.messages.Suggestion(
                title = "title$i",
                url = "url$i",
                body = "body$i",
                type = "type$i",
                context = listOf("context$i"),
                id = i % numSuggestionsPerEvent
            )
            expectedSuggestions.add(suggestion)
        }

        val expectedSequence = listOf(
            // Start message
            ChatMessage(
                tabId = testTabId,
                triggerId = testTriggerId,
                messageId = "",
                messageType = ChatMessageType.AnswerStream,
                message = ""
            ),
            ChatMessage(
                tabId = testTabId,
                triggerId = testTriggerId,
                messageId = testRequestId,
                messageType = ChatMessageType.AnswerPart,
                message = token1,
                codeReference = listOf()
            ),
            ChatMessage(
                tabId = testTabId,
                triggerId = testTriggerId,
                messageId = testRequestId,
                messageType = ChatMessageType.AnswerPart,
                message = token1+token2,
                codeReference = listOf()
            ),
            // Completion message 1
            ChatMessage(
                tabId = testTabId,
                triggerId = testTriggerId,
                messageId = testRequestId,
                messageType = ChatMessageType.AnswerPart,
                followUps = null,
                relatedSuggestions = expectedSuggestions,
                message = token1+token2
            ),
            // Completion message 2
            ChatMessage(
                tabId = testTabId,
                triggerId = testTriggerId,
                messageId = testRequestId,
                messageType = ChatMessageType.Answer,
                followUps = listOf(),
            ),
        )

        verifyChatFlow(expectedSequence, chatFlow)

        // telemetry
        verify(exactly = 1) {
            mockTelemetryHelper.setResponseStreamStartTime(testTabId)
        }
        verify(exactly = 1) {
            mockTelemetryHelper.setResponseStreamTotalTime(testTabId)
        }
        verify(exactly = 2 ){
            mockTelemetryHelper.setResponseStreamTimeForChunks(testTabId)
        }
        verify(exactly = 1) {
            mockTelemetryHelper.recordAddMessage(testData, expectedSequence.last(), (token1+token2).length, 200)
        }
    }

    @Test
    fun `Sequential ChatResponses with codeReferences handled`() {
        // === Arrange ===
        val numReferences = 4

        // Autogenerate incremental references
        val references = mutableListOf<Reference>()

        for (i in 0 until numReferences) {
            val reference = Reference(
                licenseName = "licenseName$i",
                repository = "repository$i",
                url = "url$i",
                recommendationContentSpan = RecommendationContentSpan(0, 1),
            )
            references.add(reference)
        }

        val token1 = "token1"
        val token2 = "token2"

        testChatFlow = flow {
            emit(
                ChatResponseEvent(
                    requestId = testRequestId,
                    statusCode = 200,
                    token = token1,
                    followUps = null,
                    codeReferences = references.subList(0, 2),
                    suggestions = null,
                    query = null
                ),
            )
            emit(
                ChatResponseEvent(
                    requestId = testRequestId,
                    statusCode = 200,
                    token = token2,
                    followUps = null,
                    codeReferences = references.subList(2, 4),
                    suggestions = null,
                    query = null
                )
            )
        }
        every { mockChatSession.chat(any()) } returns testChatFlow

        // === Act ===
        val chatFlow = chatPromptHandler.handle(
            tabId = testTabId,
            triggerId = testTriggerId,
            data = testData,
            sessionInfo = mockChatSessionInfo
        )

        // === Assert ===
        val expectedReferences = mutableListOf<CodeReference>()
        for (i in 0 until numReferences) {
            val codeReference = CodeReference(
                // TODO match the CodeReference creation with ChatPromptHandler line 114
                licenseName = "licenseName$i",
                repository = "repository$i",
                url = "url$i",
                recommendationContentSpan = software.aws.toolkits.jetbrains.services.cwc.messages.RecommendationContentSpan(0,1),
                information = "Reference code under **licenseName$i** license from repository `repository$i`"
            )
            expectedReferences.add(codeReference)
        }

        val expectedSequence = listOf(
            // Start message
            ChatMessage(
                tabId = testTabId,
                triggerId = testTriggerId,
                messageId = "",
                messageType = ChatMessageType.AnswerStream,
                message = ""
            ),
            ChatMessage(
                tabId = testTabId,
                triggerId = testTriggerId,
                messageId = testRequestId,
                messageType = ChatMessageType.AnswerPart,
                message = token1,
                codeReference = expectedReferences.subList(0, 2)
            ),
            ChatMessage(
                tabId = testTabId,
                triggerId = testTriggerId,
                messageId = testRequestId,
                messageType = ChatMessageType.AnswerPart,
                message = token1+token2,
                codeReference = expectedReferences.subList(0, 4)
            ),
            // Completion message
            ChatMessage(
                tabId = testTabId,
                triggerId = testTriggerId,
                messageId = testRequestId,
                messageType = ChatMessageType.Answer,
                followUps = listOf(),
            ),
        )

        verifyChatFlow(expectedSequence, chatFlow)

        // telemetry
        verify(exactly = 1) {
            mockTelemetryHelper.setResponseStreamStartTime(testTabId)
        }
        verify(exactly = 1) {
            mockTelemetryHelper.setResponseStreamTotalTime(testTabId)
        }
        verify(exactly = 2 ){
            mockTelemetryHelper.setResponseStreamTimeForChunks(testTabId)
        }
        verify(exactly = 1) {
            mockTelemetryHelper.recordAddMessage(testData, expectedSequence.last(), (token1+token2).length, 200)
        }
    }

    @Test
    fun `Sequential ChatResponses with followUps handled`() {
        // === Arrange ===
        val numFollowups = 4

        // Autogenerate incremental followUps
        val followUps = mutableListOf<SuggestedFollowUp>()

        for (i in 0 until numFollowups) {
            val followUp = SuggestedFollowUp(
                type = FollowUpType.Alternatives,
                pillText = "pillText$i",
                prompt = "prompt$i",
                message = "message$i",
                attachedSuggestions = null,
            )
            followUps.add(followUp)
        }

        val token1 = "token1"
        val token2 = "token2"

        testChatFlow = flow {
            emit(
                ChatResponseEvent(
                    requestId = testRequestId,
                    statusCode = 200,
                    token = token1,
                    followUps = followUps.subList(0,2),
                    codeReferences = null,
                    suggestions = null,
                    query = null
                ),
            )
            emit(
                ChatResponseEvent(
                    requestId = testRequestId,
                    statusCode = 200,
                    token = token2,
                    followUps = followUps.subList
                        (2,4),
                    codeReferences = null,
                    suggestions = null,
                    query = null
                )
            )
        }
        every { mockChatSession.chat(any()) } returns testChatFlow

        // === Act ===
        val chatFlow = chatPromptHandler.handle(
            tabId = testTabId,
            triggerId = testTriggerId,
            data = testData,
            sessionInfo = mockChatSessionInfo
        )

        // === Assert ===
        val expectedFollowUps = mutableListOf<FollowUp>()
        for (i in 0 until numFollowups) {
            val pillTextFollowUp = FollowUp(
                type = FollowUpType.Alternatives,
                pillText = "pillText$i",
                prompt = "prompt$i",
            )
            val messageFollowUp = FollowUp(
                type = FollowUpType.Alternatives,
                pillText = "message$i",
                prompt = "message$i",
            )
            expectedFollowUps.add(pillTextFollowUp)
            expectedFollowUps.add(messageFollowUp)
        }

        val expectedSequence = listOf(
            // Start message
            ChatMessage(
                tabId = testTabId,
                triggerId = testTriggerId,
                messageId = "",
                messageType = ChatMessageType.AnswerStream,
                message = ""
            ),
            ChatMessage(
                tabId = testTabId,
                triggerId = testTriggerId,
                messageId = testRequestId,
                messageType = ChatMessageType.AnswerPart,
                message = token1,
                followUps = null,
                codeReference = listOf()
            ),
            ChatMessage(
                tabId = testTabId,
                triggerId = testTriggerId,
                messageId = testRequestId,
                messageType = ChatMessageType.AnswerPart,
                message = token1+token2,
                followUps = null,
                codeReference = listOf()
            ),
            // Completion message
            ChatMessage(
                tabId = testTabId,
                triggerId = testTriggerId,
                messageId = testRequestId,
                messageType = ChatMessageType.Answer,
                followUps = expectedFollowUps,
            ),
        )

        verifyChatFlow(expectedSequence, chatFlow)

        // telemetry
        verify(exactly = 1) {
            mockTelemetryHelper.setResponseStreamStartTime(testTabId)
        }
        verify(exactly = 1) {
            mockTelemetryHelper.setResponseStreamTotalTime(testTabId)
        }
        verify(exactly = 2 ){
            mockTelemetryHelper.setResponseStreamTimeForChunks(testTabId)
        }
        verify(exactly = 1) {
            mockTelemetryHelper.recordAddMessage(testData, expectedSequence.last(), (token1+token2).length, 200)
        }
    }

    @Test
    fun `CodeWhispererStreamingException throws`() {
        // === Arrange ===
        testChatFlow = flow {
            throw mockk<CodeWhispererStreamingException>()
        }

        every { mockChatSession.chat(any()) } returns testChatFlow

        // === Act ===
        try {
            chatPromptHandler.handle(
                tabId = testTabId,
                triggerId = testTriggerId,
                data = testData,
                sessionInfo = mockChatSessionInfo
            )
        } catch (e: ChatApiException) {
            // === Assert ===
            verify(exactly = 1) { mockTelemetryHelper.recordMessageResponseError(testData, testTabId, 0) }

            assertThat(e).isInstanceOf(CodeWhispererStreamingException::class.java)
            assertThat(e.message).isEqualTo("Encountered exception calling the API")
            assertThat(e.sessionId).isEqualTo(mockChatSessionInfo.session.conversationId)

        }
    }

    private fun verifyChatFlow(expectedSequence: List<ChatMessage>, chatFlow: Flow<ChatMessage>) {
        var currentIndex = 0

        runBlocking {
            chatFlow.collect { value ->
                val expectedValue = expectedSequence.getOrNull(currentIndex)

                assertThat(value).isEqualTo(expectedValue)

                currentIndex++;
            }
        }
        assertThat(currentIndex).isEqualTo(expectedSequence.count())
    }


}
