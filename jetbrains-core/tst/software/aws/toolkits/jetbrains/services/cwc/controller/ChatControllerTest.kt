package software.aws.toolkits.jetbrains.services.cwc.controller

import com.intellij.ide.BrowserUtil
import com.intellij.openapi.application.Application
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
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
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import software.amazon.awssdk.services.codewhispererstreaming.model.UserIntent
import software.aws.toolkits.jetbrains.services.amazonq.apps.AmazonQAppInitContext
import software.aws.toolkits.jetbrains.services.amazonq.commands.MessageTypeRegistry
import software.aws.toolkits.jetbrains.services.amazonq.messages.MessageListener
import software.aws.toolkits.jetbrains.services.amazonq.messages.MessagePublisher
import software.aws.toolkits.jetbrains.services.amazonq.onboarding.OnboardingPageInteraction
import software.aws.toolkits.jetbrains.services.amazonq.onboarding.OnboardingPageInteractionType
import software.aws.toolkits.jetbrains.services.amazonq.webview.FqnWebviewAdapter
import software.aws.toolkits.jetbrains.services.codemodernizer.CodeModernizerManager
import software.aws.toolkits.jetbrains.services.codewhisperer.telemetry.CodeWhispererUserModificationTracker
import software.aws.toolkits.jetbrains.services.cwc.auth.AuthController
import software.aws.toolkits.jetbrains.services.cwc.auth.AuthFollowUpType
import software.aws.toolkits.jetbrains.services.cwc.clients.chat.model.ChatRequestData
import software.aws.toolkits.jetbrains.services.cwc.clients.chat.model.FollowUpType
import software.aws.toolkits.jetbrains.services.cwc.clients.chat.model.TriggerType
import software.aws.toolkits.jetbrains.services.cwc.commands.ContextMenuActionMessage
import software.aws.toolkits.jetbrains.services.cwc.commands.EditorContextCommand
import software.aws.toolkits.jetbrains.services.cwc.controller.chat.StaticPrompt
import software.aws.toolkits.jetbrains.services.cwc.controller.chat.StaticTextResponse
import software.aws.toolkits.jetbrains.services.cwc.controller.chat.messenger.ChatPromptHandler
import software.aws.toolkits.jetbrains.services.cwc.controller.chat.telemetry.InsertedCodeModificationEntry
import software.aws.toolkits.jetbrains.services.cwc.controller.chat.telemetry.TelemetryHelper
import software.aws.toolkits.jetbrains.services.cwc.controller.chat.userIntent.UserIntentRecognizer
import software.aws.toolkits.jetbrains.services.cwc.editor.context.ActiveFileContext
import software.aws.toolkits.jetbrains.services.cwc.editor.context.ActiveFileContextExtractor
import software.aws.toolkits.jetbrains.services.cwc.messages.ChatMessage
import software.aws.toolkits.jetbrains.services.cwc.messages.ChatMessageType
import software.aws.toolkits.jetbrains.services.cwc.messages.CodeReference
import software.aws.toolkits.jetbrains.services.cwc.messages.EditorContextCommandMessage
import software.aws.toolkits.jetbrains.services.cwc.messages.FocusType
import software.aws.toolkits.jetbrains.services.cwc.messages.FollowUp
import software.aws.toolkits.jetbrains.services.cwc.messages.IncomingCwcMessage
import software.aws.toolkits.jetbrains.services.cwc.messages.LinkType
import software.aws.toolkits.jetbrains.services.cwc.messages.OnboardingPageInteractionMessage
import software.aws.toolkits.jetbrains.services.cwc.messages.QuickActionMessage
import software.aws.toolkits.jetbrains.services.cwc.storage.ChatSessionInfo
import software.aws.toolkits.jetbrains.services.cwc.storage.ChatSessionStorage
import software.aws.toolkits.jetbrains.services.cwc.utility.EdtUtility
import software.aws.toolkits.telemetry.CwsprChatCommandType

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

            // waitForTabId
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
        mockkObject(ChatPromptHandler.Companion)
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
        mockkObject(ChatPromptHandler.Companion)
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
        val mockTracker = mockk<CodeWhispererUserModificationTracker>(relaxed = true)
        every {
            CodeWhispererUserModificationTracker.getInstance(any())
        } returns mockTracker

        // Mocking PsiDocumentManager
        mockkStatic(PsiDocumentManager::class)
        every { PsiDocumentManager.getInstance(any()) } returns mockk(relaxed = true)

        // TODO: Can this be in setup?
        // Mock for runInEdt
        mockkObject(EdtUtility)
        every { EdtUtility.runInEdt(any()) } answers {
            firstArg<() -> Unit>().invoke()
        }


        // Act
        runBlocking {
            chatController.processInsertCodeAtCursorPosition(testMessage)
        }


        // Assert
        verify { applicationMock.runWriteAction(any()) }
        verify { WriteCommandAction.runWriteCommandAction(any(), any()) }


        verify { mockEditor.document.deleteString(testSelectionStart, testSelectionEnd) }

        verify { mockEditor.document.insertString(testOffset, testCode) }

        verify { ReferenceLogController.addReferenceLog(testCode, testCodeReference, mockEditor, project) }

        verify {
            mockTracker.enqueue(match {
                it is InsertedCodeModificationEntry &&
                    it.messageId == testMessageId &&
                    it.originalString == testCode
            })
        }

        verify { mockEditor.document.createRangeMarker(testSelectionStart, testSelectionEnd, true) }


        coVerify { telemetryHelper.recordInteractWithMessage(match { it == testMessage }) }

        // Cleanup
        unmockkStatic(PsiDocumentManager::class)
        unmockkStatic(ApplicationManager::class)
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

    @Test
    fun `processOnboardingPageInteraction handled`() {

        // Arrange
        val testTriggerId = "testTriggerId"

        // mock context extractor
        val mockContextExtractor = mockk<ActiveFileContextExtractor>(relaxed = true)
        val testActiveFileContext = ActiveFileContext(null, null)
        coEvery { mockContextExtractor.extractContextForTrigger(any()) } returns testActiveFileContext

        // waitForTabId
        val testTabId = "testTabId"
        every { messagesFromUiToApp.flow } returns flowOf(
            IncomingCwcMessage.TriggerTabIdReceived(testTriggerId, testTabId)
        )

        val testMessage = OnboardingPageInteraction(
            OnboardingPageInteractionType.CwcButtonClick
        )

        runBlocking {
            // Act
            chatController.processOnboardingPageInteraction(testMessage, testTriggerId)
        }

        // Assert

        // Check call to sendOnboardingPageInteractionMessage
        coVerify {
            messagePublisher.publish(
                match {
                    it is OnboardingPageInteractionMessage &&
                        it.message == StaticPrompt.OnboardingHelp.message &&
                        it.interactionType == OnboardingPageInteractionType.CwcButtonClick &&
                        it.triggerId == testTriggerId
                })
        }

        // Check call to sendStaticTextResponse
        coVerify {
            messagePublisher.publish(
                match { msg ->
                    msg is ChatMessage &&
                        msg.tabId == testTabId &&
                        msg.triggerId == testTriggerId &&
                        msg.messageType == ChatMessageType.Answer &&
                        msg.messageId == "static_message_$testTriggerId" &&
                        msg.message == StaticTextResponse.OnboardingHelp.message &&
                        msg.followUps == StaticTextResponse.OnboardingHelp.followUps.map {
                        FollowUp(
                            type = FollowUpType.Generated,
                            pillText = it,
                            prompt = it,
                        )
                    } &&
                        msg.followUpsHeader == StaticTextResponse.OnboardingHelp.followUpsHeader
                }
            )
        }
    }

    @Test
    fun `processContextMenuCommand handles context menu command`() {
        val testTriggerId = "testTriggerId"

        // File context mock
        val mockFileContext = mockk<ActiveFileContext>(relaxed = true)
        val testCodeSelection = "testCodeSelection"
        every { mockFileContext.focusAreaContext?.codeSelection } returns testCodeSelection
        coEvery { contextExtractor.extractContextForTrigger(any()) } returns mockFileContext

        // waitForTabId
        val testTabId = ChatController.NO_TAB_AVAILABLE
        every { messagesFromUiToApp.flow } returns flowOf(
            IncomingCwcMessage.TriggerTabIdReceived(testTriggerId, testTabId)
        )

        // Message
        val testCommand = EditorContextCommand.SendToPrompt
        val testMessage = ContextMenuActionMessage(
            command = testCommand
        )


        // Act
        runBlocking {
            chatController.processContextMenuCommand(testMessage, testTriggerId)
        }


        // Assert

        val formattedCodeSelection = "\n```\n${testCodeSelection}\n```\n"

        coEvery {
            messagePublisher.publish(match {
                it is EditorContextCommandMessage &&
                    it.message == formattedCodeSelection &&
                    it.command == testMessage.command.actionId &&
                    it.triggerId == testTriggerId
            })
        }

        val prompt = "${testMessage.command} the following part of my code for me: $formattedCodeSelection"

        coEvery {
            messagePublisher.publish(match {
                it is EditorContextCommandMessage &&
                    it.message == prompt &&
                    it.command == testMessage.command.actionId &&
                    it.triggerId == testTriggerId
            })
        }
    }

    @Test
    fun `processLinkClick opens browser`() {

        // Arrange
        val testUrl = "testUrl"
        val testMessage = IncomingCwcMessage.ClickedLink(
            tabId = "testTabId",
            messageId = "testMessageId",
            link = testUrl,
            type = LinkType.BodyLink,
        )

        // Mocking BrowserUtil
        mockkStatic(BrowserUtil::class)
        every { BrowserUtil.browse(any<String>()) } just Runs

        // Act
        runBlocking {
            chatController.processLinkClick(testMessage)
        }

        // Assert
        verify { BrowserUtil.browse(testUrl) }
        coVerify { telemetryHelper.recordInteractWithMessage(match { it == testMessage }) }

        // Cleanup
        unmockkStatic(BrowserUtil::class)

    }
}
