package software.aws.toolkits.jetbrains.services.cwc.controller

import com.intellij.openapi.application.Application
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.testFramework.EdtTestUtil
import com.intellij.testFramework.EdtTestUtil.runInEdtAndWait
import com.intellij.testFramework.runInEdtAndWait
import io.mockk.Runs
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.verify
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockkStatic
import io.mockk.unmockkObject
import io.mockk.unmockkStatic
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.setMain
import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import software.amazon.awssdk.services.codewhispererstreaming.model.UserIntent
import software.aws.toolkits.jetbrains.services.amazonq.apps.AmazonQAppInitContext
import software.aws.toolkits.jetbrains.services.amazonq.commands.MessageTypeRegistry
import software.aws.toolkits.jetbrains.services.amazonq.messages.MessageListener
import software.aws.toolkits.jetbrains.services.amazonq.messages.MessagePublisher
import software.aws.toolkits.jetbrains.services.amazonq.webview.FqnWebviewAdapter
import software.aws.toolkits.jetbrains.services.codemodernizer.CodeModernizerManager
import software.aws.toolkits.jetbrains.services.codewhisperer.telemetry.CodeWhispererUserModificationTracker
import software.aws.toolkits.jetbrains.services.cwc.auth.AuthController
import software.aws.toolkits.jetbrains.services.cwc.auth.AuthFollowUpType
import software.aws.toolkits.jetbrains.services.cwc.clients.chat.model.ChatRequestData
import software.aws.toolkits.jetbrains.services.cwc.clients.chat.model.FollowUpType
import software.aws.toolkits.jetbrains.services.cwc.clients.chat.model.TriggerType
import software.aws.toolkits.jetbrains.services.cwc.controller.chat.StaticPrompt
import software.aws.toolkits.jetbrains.services.cwc.controller.chat.StaticTextResponse
import software.aws.toolkits.jetbrains.services.cwc.controller.chat.messenger.ChatPromptHandler
import software.aws.toolkits.jetbrains.services.cwc.controller.chat.telemetry.TelemetryHelper
import software.aws.toolkits.jetbrains.services.cwc.controller.chat.userIntent.UserIntentRecognizer
import software.aws.toolkits.jetbrains.services.cwc.editor.context.ActiveFileContext
import software.aws.toolkits.jetbrains.services.cwc.editor.context.ActiveFileContextExtractor
import software.aws.toolkits.jetbrains.services.cwc.messages.ChatMessage
import software.aws.toolkits.jetbrains.services.cwc.messages.ChatMessageType
import software.aws.toolkits.jetbrains.services.cwc.messages.CodeReference
import software.aws.toolkits.jetbrains.services.cwc.messages.FocusType
import software.aws.toolkits.jetbrains.services.cwc.messages.FollowUp
import software.aws.toolkits.jetbrains.services.cwc.messages.IncomingCwcMessage
import software.aws.toolkits.jetbrains.services.cwc.messages.QuickActionMessage
import software.aws.toolkits.jetbrains.services.cwc.storage.ChatSessionInfo
import software.aws.toolkits.jetbrains.services.cwc.storage.ChatSessionStorage
import software.aws.toolkits.telemetry.CwsprChatCommandType
import kotlin.coroutines.CoroutineContext

class ChatControllerTest {

    private lateinit var context: AmazonQAppInitContext

    // Variables used to create context object
    private val project = mockk<Project>(relaxed = true)
    private val messagePublisher = mockk<MessagePublisher>(relaxed = true)
    private val messagesFromUiToApp = mockk<MessageListener>(relaxed = true)
    private val messageTypeRegistry = mockk<MessageTypeRegistry>(relaxed = true)
    private val fqnWebviewAdapter = mockk<FqnWebviewAdapter>(relaxed = true)

    // Additional params
    private val chatSessionStorage = mockk<ChatSessionStorage>(relaxed = true)
    private val contextExtractor = mockk<ActiveFileContextExtractor>(relaxed = true)
    private val intentRecognizer = mockk<UserIntentRecognizer>(relaxed = true)
    private val authController = mockk<AuthController>(relaxed = true)
    private val telemetryHelper = mockk<TelemetryHelper>(relaxed = true)

    private lateinit var chatController: ChatController

    @Before
    fun setup() {

        context = AmazonQAppInitContext(
            project = project,
            messagesFromAppToUi = messagePublisher,
            messagesFromUiToApp = messagesFromUiToApp,
            messageTypeRegistry = messageTypeRegistry,
            fqnWebviewAdapter = fqnWebviewAdapter,
        )

        // Create chat controller
        chatController = ChatController(
            context = context,
            chatSessionStorage = chatSessionStorage,
            contextExtractor = contextExtractor,
            intentRecognizer = intentRecognizer,
            authController = authController,
            telemetryHelper = telemetryHelper,
        )
    }

    @After
    fun tearDown() {
        clearMocks(
            project,
            messagePublisher,
            messagesFromUiToApp,
            messageTypeRegistry,
            fqnWebviewAdapter,
            chatSessionStorage
        )
    }

    @Test
    fun `processClearQuickAction calls deleteSession and telemetry`() {

        // Arrange
        mockkObject(TelemetryHelper.Companion) {
            every { TelemetryHelper.recordTelemetryChatRunCommand(CwsprChatCommandType.Clear, any()) } returns Unit
            coEvery { chatSessionStorage.deleteSession("tabId") } returns Unit
            val message = IncomingCwcMessage.ClearChat("tabId")

            // Act
            runBlocking {
                chatController.processClearQuickAction(message)
            }

            // Assert
            assertThat(chatController).isNotNull // Example assertion

            // Verifying that methods were called
            verify { TelemetryHelper.recordTelemetryChatRunCommand(CwsprChatCommandType.Clear, any()) }
            coVerify { chatSessionStorage.deleteSession("tabId") }
        }

    }

    @Test
    fun `processHelpQuickAction publishes messages and calls telemetry`() {

        // Arrange
        mockkObject(TelemetryHelper.Companion) {
            every { TelemetryHelper.recordTelemetryChatRunCommand(CwsprChatCommandType.Help, any()) } returns Unit

            val testTriggerId = "testTriggerId"
            every { messagesFromUiToApp.flow } returns flowOf(
                IncomingCwcMessage.TriggerTabIdReceived(testTriggerId, "expectedTabId")
            )

            val message = IncomingCwcMessage.Help("expectedTabId")

            // Act
            runBlocking {
                chatController.processHelpQuickAction(message, testTriggerId)
            }

            // Assert

            // QuickActionMessage
            coVerify {
                messagePublisher.publish(
                    match {
                        it is QuickActionMessage &&
                            it.message == StaticPrompt.Help.message
                    }
                )
            }

            // sendStaticTextResponse
            coVerify {
                messagePublisher.publish(
                    match {
                        it is ChatMessage &&
                            it.tabId == "expectedTabId" &&
                            it.triggerId == testTriggerId &&
                            it.messageType == ChatMessageType.Answer &&
                            it.messageId == "static_message_$testTriggerId" &&
                            it.message == StaticTextResponse.Help.message &&
                            it.followUpsHeader == StaticTextResponse.Help.followUpsHeader
                    }
                )
            }

            verify { TelemetryHelper.recordTelemetryChatRunCommand(CwsprChatCommandType.Help, any()) }
        }

    }

    @Test
    fun `processTransformQuickAction publishes messages and calls telemetry`() {

        val managerMock = mockk<CodeModernizerManager>(relaxed = true)
        every { managerMock.isModernizationJobActive() } returns false

        // Arrange
        mockkObject(CodeModernizerManager.Companion) {
            every { CodeModernizerManager.getInstance(any()) } returns managerMock

            mockkObject(TelemetryHelper.Companion) {
                every { TelemetryHelper.recordTelemetryChatRunCommand(CwsprChatCommandType.Transform, any()) } returns Unit

                val testTriggerId = "testTriggerId"
                val testTabId = "testTabId"
                val message = IncomingCwcMessage.Transform(testTabId)

                // Mock the calls to ApplicationManager
                mockkStatic(ApplicationManager::class)
                val applicationMock = mockk<Application>(relaxed = true)

                // Mocking getApplication and then invokeLater
                every { ApplicationManager.getApplication() } returns applicationMock
                every { applicationMock.invokeLater(any()) } just Runs

                // Act
                runBlocking {
                    chatController.processTransformQuickAction(message, testTriggerId)
                }

                // Verify

                // QuickActionMessage
                coVerify {
                    messagePublisher.publish(
                        match {
                            it is QuickActionMessage &&
                                it.message == StaticPrompt.Transform.message
                        }
                    )
                }

                coVerify {
                    messagePublisher.publish(
                        match {
                            it is ChatMessage &&
                                it.tabId == testTabId &&
                                it.messageId == "" &&
                                it.messageType == ChatMessageType.Answer
                            // message - determined by bundle
                            // triggerId - hardcoded random UUID within method
                        }
                    )
                }

                verify { TelemetryHelper.recordTelemetryChatRunCommand(CwsprChatCommandType.Transform) }

                unmockkStatic(ApplicationManager::class)

            }
        }

    }

    @Test
    fun `processPromptChatMessage handles chat message`() {

        // Arrange
        mockkObject(ChatPromptHandler.Companion) {
            val handlerMock = mockk<ChatPromptHandler>(relaxed = true)
            every { ChatPromptHandler.create(any()) } returns handlerMock

            val testTriggerId = "testTriggerId"
            val testChatMessage = "testChatMessage"
            val testCommand = "testCommand"
            val testTabId = "testTabId"
            val testMessage = IncomingCwcMessage.ChatPrompt(
                chatMessage = testChatMessage,
                command = testCommand,
                tabId = testTabId,
                userIntent = "Explain",
            )

            val testActiveFileContext = ActiveFileContext(null, null)
            coEvery { contextExtractor.extractContextForTrigger(any()) } returns testActiveFileContext

            // Mock no auth needed
            every { authController.getAuthNeededState(any()) } returns null

            // Session History Mock
            val mockChatSessionInfo = mockk<ChatSessionInfo>(relaxed = true)
            val testHistory = mutableListOf<ChatRequestData>()
            every { mockChatSessionInfo.history } returns testHistory
            val testScope = CoroutineScope(Dispatchers.Default)
            every { mockChatSessionInfo.scope } returns testScope
            every { chatSessionStorage.getSession(any(), any()) } returns mockChatSessionInfo

            val testUserIntent = UserIntent.EXPLAIN_CODE_SELECTION
            every { intentRecognizer.getUserIntentFromPromptChatMessage(any()) } returns testUserIntent

            runBlocking {
                // Act
                chatController.processPromptChatMessage(testMessage, testTriggerId)
            }

            // Assert
            val testRequestData = ChatRequestData(
                tabId = testTabId,
                message = testChatMessage,
                activeFileContext = testActiveFileContext,
                userIntent = testUserIntent,
                triggerType = TriggerType.Click,
            )

            assertThat(
                testHistory[0] == testRequestData
            ).isTrue()

            verify { telemetryHelper.recordEnterFocusConversation(match { it == testTabId }) }
            verify { telemetryHelper.recordStartConversation(match { it == testTabId }, match { it == testRequestData }) }

            verify {
                handlerMock.handle(
                    match { it == testTabId },
                    match { it == testTriggerId },
                    match { it == testRequestData },
                    match { it == mockChatSessionInfo }
                )
            }
        }

    }


    @Test
    fun `processTabWasRemoved handles tab removed`() {
        val testTabId = "testTabId"
        val tabRemovedMessage = IncomingCwcMessage.TabRemoved(tabId = testTabId, tabType = "testTabType")

        runBlocking {
            chatController.processTabWasRemoved(tabRemovedMessage)
            coVerify { chatSessionStorage.deleteSession(testTabId) }
        }
    }


    @Test
    fun `processTabChanged handles tab changed`() {
        val testPrevTabId = "testPrevTabId"
        val testTabId = "testTabId"
        val tabChangedMessage = IncomingCwcMessage.TabChanged(tabId = testTabId, prevTabId = testPrevTabId)

        runBlocking {
            chatController.processTabChanged(tabChangedMessage)
            coVerify { telemetryHelper.recordExitFocusConversation(testPrevTabId) }
            coVerify { telemetryHelper.recordEnterFocusConversation(testTabId) }
        }
    }


    @Test
    fun `processFollowUpClick handles follow-up click`() {

        // Arrange
        mockkObject(ChatPromptHandler.Companion) {
            val handlerMock = mockk<ChatPromptHandler>(relaxed = true)
            every { ChatPromptHandler.create(any()) } returns handlerMock

            val testTriggerId = "testTriggerId"

            val testType = FollowUpType.ExplainInDetail
            val testPillText = "testPillText"
            val testPrompt = "testPrompt"
            val testFollowUp = FollowUp(
                testType,
                pillText = testPillText,
                prompt = testPrompt,
            )

            val testTabId = "testTabId"
            val testMessageId = "testMessageId"
            val testCommand = "testCommand"
            val testMessage = IncomingCwcMessage.FollowupClicked(
                followUp = testFollowUp,
                tabId = testTabId,
                messageId = testMessageId,
                command = testCommand,
            )

            val testActiveFileContext = ActiveFileContext(null, null)

            // Mock no auth needed
            every { authController.getAuthNeededState(any()) } returns null

            // Session History Mock
            val mockChatSessionInfo = mockk<ChatSessionInfo>(relaxed = true)
            val testHistory = mutableListOf<ChatRequestData>()
            every { mockChatSessionInfo.history } returns testHistory
            val testScope = CoroutineScope(Dispatchers.Default)
            every { mockChatSessionInfo.scope } returns testScope
            every { chatSessionStorage.getSession(any(), any()) } returns mockChatSessionInfo

            val testUserIntent = UserIntent.EXPLAIN_CODE_SELECTION
            every { intentRecognizer.getUserIntentFromFollowupType(any()) } returns testUserIntent

// Act
            runBlocking {
                chatController.processFollowUpClick(testMessage, testTriggerId)
            }

// Assert
            val testRequestData = ChatRequestData(
                tabId = testTabId,
                message = testPrompt,
                activeFileContext = testActiveFileContext,
                userIntent = testUserIntent,
                triggerType = TriggerType.Click,
            )

            assertThat(
                testHistory[0] == testRequestData
            ).isTrue()

            verify { telemetryHelper.recordEnterFocusConversation(match { it == testTabId }) }
            verify { telemetryHelper.recordStartConversation(match { it == testTabId }, match { it == testRequestData }) }

            verify {
                handlerMock.handle(
                    match { it == testTabId },
                    match { it == testTriggerId },
                    match { it == testRequestData },
                    match { it == mockChatSessionInfo }
                )
            }

            coVerify { telemetryHelper.recordInteractWithMessage(match { it == testMessage }) }
        }
    }


    @Test
    fun `processCodeWasCopiedToClipboard handles code copy`() {

        // Arrange
        val testMessage = IncomingCwcMessage.CopyCodeToClipboard(
            tabId = "testTabId",
            messageId = "testMessageId",
            command = "testCommand",
            code = "testCode",
            insertionTargetType = "testInsertionTargetType",
        )

        // Act
        runBlocking {
            chatController.processCodeWasCopiedToClipboard(testMessage)
        }

        // Assert
        coVerify { telemetryHelper.recordInteractWithMessage(match { it == testMessage }) }
    }

    @Test
    fun `processInsertCodeAtCursorPosition handles code insertion`() {

        // Arrange
        // Convert all inputs to variables
        val testTabId = "testTabId"
        val testMessageId = "testMessageId"
        val testCode = "testCode"
        val testInsertionTargetType = "testInsertionTargetType"
        val testCodeReference = listOf<CodeReference>()

        val testMessage = IncomingCwcMessage.InsertCodeAtCursorPosition(
            tabId = testTabId,
            messageId = testMessageId,
            code = testCode,
            insertionTargetType = testInsertionTargetType,
            codeReference = testCodeReference,
        )

        // Mock Editor Access
        val mockEditor = mockk<Editor>(relaxed = true)
        mockkStatic(FileEditorManager::class)

        val mockFileEditorManager = mockk<FileEditorManager>(relaxed = true)

        every { FileEditorManager.getInstance(any()) } returns mockFileEditorManager
        every { mockFileEditorManager.selectedTextEditor } returns mockEditor

        // Mock Caret
        val mockCaret = mockk<Caret>(relaxed = true)
        every { mockEditor.caretModel.primaryCaret } returns mockCaret
        val testOffset = 5;
        every { mockCaret.offset } returns testOffset
        every { mockCaret.hasSelection() } returns true
        val testSelectionStart = 0;
        val testSelectionEnd = 100;
        every { mockCaret.selectionStart } returns testSelectionStart
        every { mockCaret.selectionEnd } returns testSelectionEnd

        // Mock ApplicationManager
        mockkStatic(ApplicationManager::class)
        val applicationMock = mockk<Application>(relaxed = true)

        // Mocking getApplication and then invokeLater
        every { ApplicationManager.getApplication() } returns applicationMock
        // Mocking runWriteAction to execute its Runnable argument
        every { applicationMock.runWriteAction(any<Runnable>()) } answers {
            firstArg<Runnable>().run()
        }

        // Mocking WriteCommandAction
        mockkStatic(WriteCommandAction::class)
        every { WriteCommandAction.runWriteCommandAction(any(), any<Runnable>()) } answers { secondArg<Runnable>().run() }

        // Mocking ReferenceLogController
        mockkObject(ReferenceLogController)
        every {
            ReferenceLogController.addReferenceLog(any(), any(), any(), any())
        } just Runs

        // Mocking CodeWhispererUserModificationTracker
        mockkObject(CodeWhispererUserModificationTracker.Companion)
        every {
            CodeWhispererUserModificationTracker.getInstance(any()).enqueue(any())
        } just Runs


        // Act
        runInEdtAndWait { // Tried this to fix it ...
            runBlocking {
                chatController.processInsertCodeAtCursorPosition(testMessage)
            }
        }

        // TODO: doesn't work - the code inside the runWriteAction is not executed
        /*
        verify { applicationMock.runWriteAction(any()) }
        verify { WriteCommandAction.runWriteCommandAction(any(), any()) }

        // Assert
        verify { mockEditor.document.deleteString(testSelectionStart, testSelectionEnd) }

        verify { mockEditor.document.insertString(testOffset, testCode) }

        verify { ReferenceLogController.addReferenceLog(testCode, testCodeReference, mockEditor, project) }

        // TODO expand
        verify { CodeWhispererUserModificationTracker.getInstance(project).enqueue(any()) }

        coVerify { telemetryHelper.recordInteractWithMessage(match { it == testMessage }) }
         */

        // Cleanup
        unmockkStatic(FileEditorManager::class)
        unmockkStatic(ApplicationManager::class)
        unmockkStatic(WriteCommandAction::class)
        unmockkObject(TelemetryHelper.Companion)
    }

    @Test
    fun `processStopResponseMessage cancels child jobs`() {
        // Arrange
        val testTabId = "testTabId"
        val testMessage = IncomingCwcMessage.StopResponse(
            tabId = testTabId,
        )

        val mockChatSessionInfo = mockk<ChatSessionInfo>(relaxed = true);

        every { chatSessionStorage.getSession(any(), any()) } returns mockChatSessionInfo

        val mockJob = mockk<Job>(relaxed = true)
        every { mockChatSessionInfo.scope.coroutineContext.job } returns mockJob

        // Act
        runBlocking {
            chatController.processStopResponseMessage(testMessage)
        }

        // Assert
        verify { mockJob.cancelChildren() }
    }

    @Test
    fun `processChatItemVoted records telemetry`() {
        val testMessage = IncomingCwcMessage.ChatItemVoted(
            tabId = "testTabId",
            messageId = "testMessageId",
            vote = "testVote",
        )

        runBlocking {
            chatController.processChatItemVoted(testMessage)
        }

        coVerify { telemetryHelper.recordInteractWithMessage(match { it == testMessage }) }
    }

    @Test
    fun `processChatItemFeedback records telemetry`() {
        val testMessage = IncomingCwcMessage.ChatItemFeedback(
            tabId = "testTabId",
            messageId = "testMessageId",
            comment = "testComment",
            selectedOption = "testSelectedOption",
        )

        runBlocking {
            chatController.processChatItemFeedback(testMessage)
        }

        coVerify { telemetryHelper.recordInteractWithMessage(match { it == testMessage }) }
    }

    @Test
    fun `processUIFocus records telemetry`() {
        val testMessage = IncomingCwcMessage.UIFocus(
            command = "testCommand",
            type = FocusType.FOCUS
        )

        runBlocking {
            chatController.processUIFocus(testMessage)
        }

        coVerify { telemetryHelper.recordEnterFocusChat() }
    }

    @Test
    fun `processAuthFollowUpClick handles auth`() {
        val testAuthType = AuthFollowUpType.FullAuth
        val testMessage = IncomingCwcMessage.AuthFollowUpWasClicked(
            tabId = "testTabId",
            authType = testAuthType,
        )

        runBlocking {
            chatController.processAuthFollowUpClick(testMessage)
        }

        coVerify { authController.handleAuth(any(), testAuthType) }
    }
}
