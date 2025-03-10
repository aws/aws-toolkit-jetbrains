// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonqFeatureDev.controller

import com.intellij.testFramework.LightVirtualFile
import com.intellij.testFramework.RuleChain
import com.intellij.testFramework.replaceService
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.runs
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.doNothing
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.reset
import org.mockito.kotlin.spy
import org.mockito.kotlin.times
import org.mockito.kotlin.whenever
import software.aws.toolkits.jetbrains.common.util.selectFolder
import software.aws.toolkits.jetbrains.services.amazonq.apps.AmazonQAppInitContext
import software.aws.toolkits.jetbrains.services.amazonq.auth.AuthController
import software.aws.toolkits.jetbrains.services.amazonq.auth.AuthNeededStates
import software.aws.toolkits.jetbrains.services.amazonq.messages.MessagePublisher
import software.aws.toolkits.jetbrains.services.amazonq.project.FeatureDevSessionContext
import software.aws.toolkits.jetbrains.services.amazonqFeatureDev.CodeIterationLimitException
import software.aws.toolkits.jetbrains.services.amazonqFeatureDev.ContentLengthException
import software.aws.toolkits.jetbrains.services.amazonqFeatureDev.EmptyPatchException
import software.aws.toolkits.jetbrains.services.amazonqFeatureDev.FeatureDevException
import software.aws.toolkits.jetbrains.services.amazonqFeatureDev.FeatureDevTestBase
import software.aws.toolkits.jetbrains.services.amazonqFeatureDev.GuardrailsException
import software.aws.toolkits.jetbrains.services.amazonqFeatureDev.MetricDataOperationName
import software.aws.toolkits.jetbrains.services.amazonqFeatureDev.MetricDataResult
import software.aws.toolkits.jetbrains.services.amazonqFeatureDev.MonthlyConversationLimitError
import software.aws.toolkits.jetbrains.services.amazonqFeatureDev.NoChangeRequiredException
import software.aws.toolkits.jetbrains.services.amazonqFeatureDev.PromptRefusalException
import software.aws.toolkits.jetbrains.services.amazonqFeatureDev.ThrottlingException
import software.aws.toolkits.jetbrains.services.amazonqFeatureDev.ZipFileCorruptedException
import software.aws.toolkits.jetbrains.services.amazonqFeatureDev.clients.FeatureDevClient
import software.aws.toolkits.jetbrains.services.amazonqFeatureDev.messages.FeatureDevMessageType
import software.aws.toolkits.jetbrains.services.amazonqFeatureDev.messages.FollowUp
import software.aws.toolkits.jetbrains.services.amazonqFeatureDev.messages.FollowUpStatusType
import software.aws.toolkits.jetbrains.services.amazonqFeatureDev.messages.FollowUpTypes
import software.aws.toolkits.jetbrains.services.amazonqFeatureDev.messages.IncomingFeatureDevMessage
import software.aws.toolkits.jetbrains.services.amazonqFeatureDev.messages.sendAnswer
import software.aws.toolkits.jetbrains.services.amazonqFeatureDev.messages.sendAsyncEventProgress
import software.aws.toolkits.jetbrains.services.amazonqFeatureDev.messages.sendChatInputEnabledMessage
import software.aws.toolkits.jetbrains.services.amazonqFeatureDev.messages.sendCodeResult
import software.aws.toolkits.jetbrains.services.amazonqFeatureDev.messages.sendSystemPrompt
import software.aws.toolkits.jetbrains.services.amazonqFeatureDev.messages.sendUpdatePlaceholder
import software.aws.toolkits.jetbrains.services.amazonqFeatureDev.messages.updateFileComponent
import software.aws.toolkits.jetbrains.services.amazonqFeatureDev.session.CodeGenerationState
import software.aws.toolkits.jetbrains.services.amazonqFeatureDev.session.DeletedFileInfo
import software.aws.toolkits.jetbrains.services.amazonqFeatureDev.session.DiffMetricsProcessed
import software.aws.toolkits.jetbrains.services.amazonqFeatureDev.session.Interaction
import software.aws.toolkits.jetbrains.services.amazonqFeatureDev.session.NewFileZipInfo
import software.aws.toolkits.jetbrains.services.amazonqFeatureDev.session.PrepareCodeGenerationState
import software.aws.toolkits.jetbrains.services.amazonqFeatureDev.session.Session
import software.aws.toolkits.jetbrains.services.amazonqFeatureDev.session.SessionStateConfig
import software.aws.toolkits.jetbrains.services.amazonqFeatureDev.session.SessionStatePhase
import software.aws.toolkits.jetbrains.services.amazonqFeatureDev.storage.ChatSessionStorage
import software.aws.toolkits.jetbrains.services.amazonqFeatureDev.util.CancellationTokenSource
import software.aws.toolkits.jetbrains.services.amazonqFeatureDev.util.FeatureDevService
import software.aws.toolkits.jetbrains.services.amazonqFeatureDev.util.InsertAction
import software.aws.toolkits.jetbrains.services.amazonqFeatureDev.util.getFollowUpOptions
import software.aws.toolkits.jetbrains.services.amazonqFeatureDev.util.uploadArtifactToS3
import software.aws.toolkits.resources.message
import software.aws.toolkits.telemetry.AmazonqTelemetry
import org.mockito.kotlin.verify as mockitoVerify

class FeatureDevControllerTest : FeatureDevTestBase() {
    @Rule
    @JvmField
    val ruleChain = RuleChain(projectRule, disposableRule)

    private lateinit var controller: FeatureDevController
    private lateinit var messenger: MessagePublisher
    private lateinit var chatSessionStorage: ChatSessionStorage
    private lateinit var appContext: AmazonQAppInitContext
    private lateinit var authController: AuthController
    private lateinit var spySession: Session
    private lateinit var featureDevClient: FeatureDevClient

    private val newFileContents =
        listOf(
            NewFileZipInfo("test.ts", "This is a comment", false, false),
            NewFileZipInfo("test2.ts", "This is a rejected file", true, false),
        )
    private val deletedFiles =
        listOf(
            DeletedFileInfo("delete.ts", false, false),
            DeletedFileInfo("delete2.ts", true, false),
        )

    @Before
    override fun setup() {
        super.setup()

        featureDevClient = mock()
        messenger = mock()
        chatSessionStorage = mock()
        projectRule.project.replaceService(FeatureDevClient::class.java, featureDevClient, disposableRule.disposable)
        appContext =
            mock<AmazonQAppInitContext> {
                on { project }.thenReturn(project)
                on { messagesFromAppToUi }.thenReturn(messenger)
            }
        authController = spy(AuthController())
        doReturn(AuthNeededStates()).`when`(authController).getAuthNeededStates(any())
        spySession = spy(Session(testTabId, project))

        mockkStatic(
            MessagePublisher::sendAnswer,
            MessagePublisher::sendSystemPrompt,
            MessagePublisher::sendUpdatePlaceholder,
            MessagePublisher::sendChatInputEnabledMessage,
            MessagePublisher::sendCodeResult,
            MessagePublisher::updateFileComponent,
        )

        mockkStatic("software.aws.toolkits.jetbrains.services.amazonqFeatureDev.util.UploadArtifactKt")
        every { uploadArtifactToS3(any(), any(), any(), any(), any()) } just runs

        controller = spy(FeatureDevController(appContext, chatSessionStorage, authController))
    }

    @After
    fun clear() {
        unmockkAll()
    }

    @Test
    fun `test new tab opened`() {
        val message = IncomingFeatureDevMessage.NewTabCreated("new-tab-created", testTabId)
        spySession = spy(Session("tabId", project))
        whenever(chatSessionStorage.getSession(any(), any())).thenReturn(spySession)
        reset(authController) // needed to have actual logic to test the isAuthenticating later

        runTest {
            controller.processNewTabCreatedMessage(message)
        }
        mockitoVerify(authController, times(1)).getAuthNeededStates(project)
        mockitoVerify(chatSessionStorage, times(1)).getSession(testTabId, project)
        assertThat(spySession.isAuthenticating).isTrue()
    }

    @Test
    fun `test newTask and closeSession followUp`() {
        /*
            Testing both followups together as they share logic, atm they could be verified together.
         */
        val followUp = FollowUp(FollowUpTypes.NEW_TASK, pillText = "Work on new task")
        val message = IncomingFeatureDevMessage.FollowupClicked(followUp, testTabId, "", "test-command")

        whenever(featureDevClient.createTaskAssistConversation()).thenReturn(exampleCreateTaskAssistConversationResponse)
        whenever(featureDevClient.sendFeatureDevTelemetryEvent(any())).thenReturn(exampleSendTelemetryEventResponse)
        whenever(chatSessionStorage.getSession(any(), any())).thenReturn(spySession)
        doNothing().`when`(chatSessionStorage).deleteSession(any())

        mockkObject(AmazonqTelemetry)
        every { AmazonqTelemetry.endChat(amazonqConversationId = any(), amazonqEndOfTheConversationLatency = any()) } just runs

        runTest {
            spySession.preloader(messenger)
            controller.processFollowupClickedMessage(message)
        }

        mockitoVerify(chatSessionStorage, times(1)).deleteSession(testTabId)

        coVerifyOrder {
            messenger.sendAnswer(testTabId, message("amazonqFeatureDev.chat_message.ask_for_new_task"), messageType = FeatureDevMessageType.Answer)
            messenger.sendUpdatePlaceholder(testTabId, message("amazonqFeatureDev.placeholder.new_plan"))
        }

        verify(
            exactly = 1,
        ) { AmazonqTelemetry.endChat(amazonqConversationId = testConversationId, amazonqEndOfTheConversationLatency = any(), createTime = any()) }
    }

    @Test
    fun `test provideFeedbackAndRegenerateCode`() =
        runTest {
            val followUp = FollowUp(FollowUpTypes.PROVIDE_FEEDBACK_AND_REGENERATE_CODE, pillText = "Regenerate code")
            val message = IncomingFeatureDevMessage.FollowupClicked(followUp, testTabId, "", "test-command")

            whenever(featureDevClient.createTaskAssistConversation()).thenReturn(exampleCreateTaskAssistConversationResponse)
            whenever(featureDevClient.sendFeatureDevTelemetryEvent(any())).thenReturn(exampleSendTelemetryEventResponse)
            whenever(chatSessionStorage.getSession(any(), any())).thenReturn(spySession)

            mockkObject(AmazonqTelemetry)
            every { AmazonqTelemetry.isProvideFeedbackForCodeGen(amazonqConversationId = any(), enabled = any()) } just runs

            spySession.preloader(messenger)
            controller.processFollowupClickedMessage(message)

            coVerifyOrder {
                AmazonqTelemetry.isProvideFeedbackForCodeGen(amazonqConversationId = testConversationId, enabled = true, createTime = any())
                messenger.sendAsyncEventProgress(testTabId, inProgress = false)
                messenger.sendAnswer(
                    tabId = testTabId,
                    message = message("amazonqFeatureDev.code_generation.provide_code_feedback"),
                    messageType = FeatureDevMessageType.Answer,
                    canBeVoted = true,
                )
                messenger.sendUpdatePlaceholder(testTabId, message("amazonqFeatureDev.placeholder.provide_code_feedback"))
            }
        }

    @Test
    fun `test insertCode`() =
        runTest {
            val followUp = FollowUp(FollowUpTypes.INSERT_CODE, pillText = "Insert code")
            val message = IncomingFeatureDevMessage.FollowupClicked(followUp, testTabId, "", "test-command")

            var featureDevService = mockk<FeatureDevService>()
            val repoContext = mock<FeatureDevSessionContext>()
            val sessionStateConfig = SessionStateConfig(testConversationId, repoContext, featureDevService)
            mockkObject(AmazonqTelemetry)
            every {
                AmazonqTelemetry.isAcceptedCodeChanges(amazonqNumberOfFilesAccepted = any(), amazonqConversationId = any(), enabled = any())
            } just runs

            whenever(featureDevClient.createTaskAssistConversation()).thenReturn(exampleCreateTaskAssistConversationResponse)
            whenever(featureDevClient.sendFeatureDevTelemetryEvent(any())).thenReturn(exampleSendTelemetryEventResponse)
            whenever(chatSessionStorage.getSession(any(), any())).thenReturn(spySession)
            whenever(spySession.sessionState).thenReturn(
                PrepareCodeGenerationState(
                    testTabId,
                    CancellationTokenSource(),
                    "test-command",
                    sessionStateConfig,
                    newFileContents,
                    deletedFiles,
                    testReferences,
                    testUploadId,
                    0,
                    messenger,
                    0,
                    0,
                    diffMetricsProcessed = DiffMetricsProcessed(HashSet(), HashSet()),
                ),
            )

            doReturn(Unit).whenever(spySession).insertChanges(any(), any(), any())
            doReturn(Unit).whenever(spySession).insertNewFiles(any())
            doReturn(Unit).whenever(spySession).applyDeleteFiles(any())

            spySession.preloader(messenger)
            controller.processFollowupClickedMessage(message)

            mockitoVerify(
                spySession,
                times(1),
            ).insertChanges(newFileContents, deletedFiles, testReferences) // updates for all files
            coVerifyOrder {
                AmazonqTelemetry.isAcceptedCodeChanges(
                    amazonqNumberOfFilesAccepted = 2.0, // it should be 2 files per test setup
                    amazonqConversationId = spySession.conversationId,
                    enabled = true,
                    createTime = any(),
                )

                // insert changes for only non rejected files
                spySession.insertNewFiles(listOf(newFileContents[0]))
                spySession.applyDeleteFiles(listOf(deletedFiles[0]))

                spySession.updateFilesPaths(
                    filePaths = newFileContents,
                    deletedFiles = deletedFiles,
                    messenger
                )
                messenger.sendAnswer(
                    tabId = testTabId,
                    message = message("amazonqFeatureDev.code_generation.updated_code"),
                    messageType = FeatureDevMessageType.Answer,
                    canBeVoted = true,
                )
                messenger.sendSystemPrompt(
                    testTabId,
                    listOf(
                        FollowUp(FollowUpTypes.NEW_TASK, message("amazonqFeatureDev.follow_up.new_task"), status = FollowUpStatusType.Info),
                        FollowUp(FollowUpTypes.CLOSE_SESSION, message("amazonqFeatureDev.follow_up.close_session"), status = FollowUpStatusType.Info),
                        FollowUp(FollowUpTypes.GENERATE_DEV_FILE, message("amazonqFeatureDev.follow_up.generate_dev_file"), status = FollowUpStatusType.Info)
                    ),
                )
                messenger.sendUpdatePlaceholder(testTabId, message("amazonqFeatureDev.placeholder.additional_improvements"))
            }
        }

    @Test
    fun `test handleChat onCodeGeneration succeeds to create files`() =
        runTest {
            val mockInteraction = mock<Interaction>()
            val featureDevService = mockk<FeatureDevService>()
            val repoContext = mock<FeatureDevSessionContext>()
            val sessionStateConfig = SessionStateConfig(testConversationId, repoContext, featureDevService)
            mockkObject(AmazonqTelemetry)
            val mockSession = mock<Session>()
            whenever(mockSession.send(userMessage)).thenReturn(mockInteraction)
            whenever(mockSession.conversationId).thenReturn(testConversationId)
            whenever(mockSession.sessionState).thenReturn(
                PrepareCodeGenerationState(
                    testTabId,
                    CancellationTokenSource(),
                    "test-command",
                    sessionStateConfig,
                    newFileContents,
                    deletedFiles,
                    testReferences,
                    testUploadId,
                    0,
                    messenger,
                    diffMetricsProcessed = DiffMetricsProcessed(HashSet(), HashSet()),
                ),
            )

            controller.onCodeGeneration(mockSession, userMessage, testTabId)

            coVerifyOrder {
                messenger.sendAsyncEventProgress(testTabId, true, message("amazonqFeatureDev.chat_message.start_code_generation_retry"))
                messenger.sendAnswer(testTabId, message("amazonqFeatureDev.chat_message.requesting_changes"), messageType = FeatureDevMessageType.AnswerStream)
                messenger.sendUpdatePlaceholder(testTabId, message("amazonqFeatureDev.placeholder.generating_code"))
                messenger.sendCodeResult(testTabId, testUploadId, newFileContents, deletedFiles, testReferences)
                messenger.sendSystemPrompt(testTabId, getFollowUpOptions(SessionStatePhase.CODEGEN, InsertAction.ALL))
                messenger.sendUpdatePlaceholder(testTabId, message("amazonqFeatureDev.placeholder.after_code_generation"))
                messenger.sendAsyncEventProgress(testTabId, false)
                messenger.sendChatInputEnabledMessage(testTabId, false)
            }
        }

    @Test(expected = RuntimeException::class)
    fun `test handleChat onCodeGeneration throws error when sending message to state`() =
        runTest {
            val mockSession = mock<Session>()

            whenever(mockSession.send(userMessage)).thenThrow(RuntimeException())
            whenever(mockSession.conversationId).thenReturn(testConversationId)

            controller.onCodeGeneration(mockSession, userMessage, testTabId)

            coVerifyOrder {
                messenger.sendAsyncEventProgress(testTabId, true, message("amazonqFeatureDev.chat_message.start_code_generation"))
                messenger.sendAnswer(testTabId, message("amazonqFeatureDev.chat_message.requesting_changes"), messageType = FeatureDevMessageType.AnswerStream)
                messenger.sendUpdatePlaceholder(testTabId, message("amazonqFeatureDev.placeholder.generating_code"))
                messenger.sendAsyncEventProgress(testTabId, false)
                messenger.sendChatInputEnabledMessage(testTabId, false)
            }
        }

    @Test
    fun `test handleChat onCodeGeneration doesn't return any files with retries`() =
        runTest {
            val filePaths = emptyList<NewFileZipInfo>()
            val deletedFiles = emptyList<DeletedFileInfo>()

            val mockInteraction = mock<Interaction>()

            val mockSession = mock<Session>()
            whenever(mockSession.send(userMessage)).thenReturn(mockInteraction)
            whenever(mockSession.conversationId).thenReturn(testConversationId)
            whenever(mockSession.sessionState).thenReturn(
                PrepareCodeGenerationState(
                    testTabId,
                    CancellationTokenSource(),
                    "",
                    mock(),
                    filePaths,
                    deletedFiles,
                    testReferences,
                    testUploadId,
                    0,
                    messenger,
                    diffMetricsProcessed = DiffMetricsProcessed(HashSet(), HashSet()),
                ),
            )
            whenever(mockSession.retries).thenReturn(3)

            controller.onCodeGeneration(mockSession, userMessage, testTabId)

            coVerifyOrder {
                messenger.sendAnswer(testTabId, message("amazonqFeatureDev.code_generation.no_file_changes"), messageType = FeatureDevMessageType.Answer)
                messenger.sendSystemPrompt(
                    testTabId,
                    listOf(FollowUp(FollowUpTypes.RETRY, message("amazonqFeatureDev.follow_up.retry"), status = FollowUpStatusType.Warning)),
                )
                messenger.sendChatInputEnabledMessage(testTabId, false)
            }
        }

    @Test
    fun `test handleChat onCodeGeneration doesn't return any files no retries`() =
        runTest {
            val filePaths = emptyList<NewFileZipInfo>()
            val deletedFiles = emptyList<DeletedFileInfo>()

            val mockInteraction = mock<Interaction>()

            val mockSession = mock<Session>()
            whenever(mockSession.send(userMessage)).thenReturn(mockInteraction)
            whenever(mockSession.conversationId).thenReturn(testConversationId)
            whenever(mockSession.sessionState).thenReturn(
                PrepareCodeGenerationState(
                    testTabId,
                    CancellationTokenSource(),
                    "",
                    mock(),
                    filePaths,
                    deletedFiles,
                    testReferences,
                    testUploadId,
                    0,
                    messenger,
                    diffMetricsProcessed = DiffMetricsProcessed(HashSet(), HashSet()),
                ),
            )
            whenever(mockSession.retries).thenReturn(0)

            controller.onCodeGeneration(mockSession, userMessage, testTabId)

            coVerifyOrder {
                messenger.sendAnswer(testTabId, message("amazonqFeatureDev.code_generation.no_file_changes"), messageType = FeatureDevMessageType.Answer)
                messenger.sendSystemPrompt(testTabId, emptyList())
                messenger.sendChatInputEnabledMessage(testTabId, false)
            }
        }

    @Test
    fun `test handleChat onCodeGeneration sends correct add code messages`() = runTest {
        val totalIterations = 10

        for (remainingIterations in 0 until totalIterations) {
            val message = if (remainingIterations > 2) {
                message("amazonqFeatureDev.code_generation.iteration_counts_ask_to_add_code_or_feedback")
            } else if (remainingIterations > 0) {
                message(
                    "amazonqFeatureDev.code_generation.iteration_counts",
                    remainingIterations,
                    totalIterations,
                )
            } else {
                message(
                    "amazonqFeatureDev.code_generation.iteration_counts_ask_to_add_code",
                    remainingIterations,
                    totalIterations,
                )
            }
            val mockSession = mock<Session>()
            val featureDevService = mockk<FeatureDevService>()
            val repoContext = mock<FeatureDevSessionContext>()
            val sessionStateConfig = SessionStateConfig(testConversationId, repoContext, featureDevService)
            val mockInteraction = mock<Interaction>()
            whenever(mockSession.send(userMessage)).thenReturn(mockInteraction)
            whenever(mockSession.sessionState).thenReturn(
                PrepareCodeGenerationState(
                    testTabId,
                    CancellationTokenSource(),
                    "test-command",
                    sessionStateConfig,
                    newFileContents,
                    deletedFiles,
                    testReferences,
                    testUploadId,
                    1,
                    messenger,
                    remainingIterations,
                    totalIterations,
                    diffMetricsProcessed = DiffMetricsProcessed(HashSet(), HashSet()),
                ),
            )

            controller.onCodeGeneration(mockSession, userMessage, testTabId)

            coVerify {
                messenger.sendAnswer(
                    tabId = testTabId,
                    messageType = FeatureDevMessageType.Answer,
                    message = message
                )
            }
        }
    }

    @Test
    fun `test handleChat onCodeGeneration sends correct messages after cancellation`() = runTest {
        val totalIterations = 10

        for (remainingIterations in -1 until totalIterations) {
            // remainingIterations < 0 is to represent the null case
            val message = if (remainingIterations > 2 || remainingIterations < 0) {
                message("amazonqFeatureDev.code_generation.stopped_code_generation_no_iteration_count_display")
            } else if (remainingIterations > 0) {
                message(
                    "amazonqFeatureDev.code_generation.stopped_code_generation",
                    remainingIterations,
                    totalIterations,
                )
            } else {
                message(
                    "amazonqFeatureDev.code_generation.stopped_code_generation_no_iterations",
                    remainingIterations,
                    totalIterations,
                )
            }
            val mockSession = mock<Session>()
            val featureDevService = mockk<FeatureDevService>()
            val repoContext = mock<FeatureDevSessionContext>()
            val sessionStateConfig = SessionStateConfig(testConversationId, repoContext, featureDevService)
            val mockInteraction = mock<Interaction>()
            val token = CancellationTokenSource()
            token.cancel()
            whenever(mockSession.send(userMessage)).thenReturn(mockInteraction)
            whenever(mockSession.sessionState).thenReturn(
                PrepareCodeGenerationState(
                    testTabId,
                    token,
                    "test-command",
                    sessionStateConfig,
                    newFileContents,
                    deletedFiles,
                    testReferences,
                    testUploadId,
                    1,
                    messenger,
                    (if (remainingIterations < 0) null else remainingIterations),
                    totalIterations,
                    diffMetricsProcessed = DiffMetricsProcessed(HashSet(), HashSet()),
                ),
            )

            controller.onCodeGeneration(mockSession, userMessage, testTabId)

            coVerify {
                messenger.sendAnswer(
                    tabId = testTabId,
                    messageType = FeatureDevMessageType.Answer,
                    message = message
                )
            }
        }
    }

    @Test
    fun `test handleChat onCodeGeneration sends success metrics`() = runTest {
        val mockSession = mock<Session>()
        val featureDevService = mockk<FeatureDevService>()
        val repoContext = mock<FeatureDevSessionContext>()
        val sessionStateConfig = SessionStateConfig(testConversationId, repoContext, featureDevService)
        val mockInteraction = mock<Interaction>()
        whenever(mockSession.send(userMessage)).thenReturn(mockInteraction)
        whenever(mockSession.sessionState).thenReturn(
            PrepareCodeGenerationState(
                testTabId,
                CancellationTokenSource(),
                "test-command",
                sessionStateConfig,
                newFileContents,
                deletedFiles,
                testReferences,
                testUploadId,
                0,
                messenger,
                diffMetricsProcessed = DiffMetricsProcessed(HashSet(), HashSet()),
            ),
        )

        controller.onCodeGeneration(mockSession, userMessage, testTabId)

        val mockInOrder = inOrder(mockSession)

        mockInOrder.verify(mockSession).sendMetricDataTelemetry(
            MetricDataOperationName.StartCodeGeneration,
            MetricDataResult.Success

        )
        mockInOrder.verify(mockSession).sendMetricDataTelemetry(
            MetricDataOperationName.EndCodeGeneration,
            MetricDataResult.Success
        )
    }

    @Test
    fun `test handleChat onCodeGeneration sends correct failure metrics for different errors`() = runTest {
        data class ErrorTestCase(
            val error: Exception,
            val expectedMetricResult: MetricDataResult,
        )

        val testCases = listOf(
            ErrorTestCase(
                EmptyPatchException("EmptyPatchException", "Empty patch"),
                MetricDataResult.LlmFailure
            ),
            ErrorTestCase(
                GuardrailsException(operation = "GenerateCode", desc = "Failed guardrails"),
                MetricDataResult.Error
            ),
            ErrorTestCase(
                PromptRefusalException(operation = "GenerateCode", desc = "Prompt refused"),
                MetricDataResult.Error
            ),
            ErrorTestCase(
                NoChangeRequiredException(operation = "GenerateCode", desc = "No changes needed"),
                MetricDataResult.Error
            ),
            ErrorTestCase(
                ThrottlingException(operation = "GenerateCode", desc = "Request throttled"),
                MetricDataResult.Error
            ),
            ErrorTestCase(
                MonthlyConversationLimitError(message = "Monthly limit reached", operation = "GenerateCode", desc = "Monthly limit reached"),
                MetricDataResult.Error
            ),
            ErrorTestCase(
                CodeIterationLimitException(operation = "GenerateCode", desc = "Code iteration limit reached"),
                MetricDataResult.Error
            ),
            ErrorTestCase(
                ContentLengthException(operation = "GenerateCode", desc = "Repo size is exceeding the limits"),
                MetricDataResult.Error
            ),
            ErrorTestCase(
                ZipFileCorruptedException(operation = "GenerateCode", desc = "Zipped file is corrupted"),
                MetricDataResult.Error
            ),
            ErrorTestCase(
                FeatureDevException(message = "Resource not found", operation = "GenerateCode", desc = null),
                MetricDataResult.Error
            ),
            ErrorTestCase(
                RuntimeException("Unknown error"),
                MetricDataResult.Fault
            )
        )

        testCases.forEach { (error, expectedResult) ->
            val mockSession = mock<Session>()
            whenever(mockSession.send(userMessage)).thenThrow(error)
            whenever(mockSession.sessionState).thenReturn(
                CodeGenerationState(
                    testTabId,
                    "",
                    mock(),
                    testUploadId,
                    0,
                    0.0,
                    messenger,
                    token = CancellationTokenSource(),
                    diffMetricsProcessed = DiffMetricsProcessed(HashSet(), HashSet())
                )
            )

            assertThrows<Exception> {
                controller.onCodeGeneration(mockSession, userMessage, testTabId)
            }

            val mockInOrder = inOrder(mockSession)

            mockInOrder.verify(mockSession).sendMetricDataTelemetry(
                MetricDataOperationName.StartCodeGeneration,
                MetricDataResult.Success

            )
            mockInOrder.verify(mockSession).sendMetricDataTelemetry(
                MetricDataOperationName.EndCodeGeneration,
                expectedResult
            )
        }
    }

    @Test
    fun `test processFileClicked handles file rejection`() =
        runTest {
            val message = IncomingFeatureDevMessage.FileClicked(testTabId, newFileContents[0].zipFilePath, "", "reject-change")

            whenever(featureDevClient.createTaskAssistConversation()).thenReturn(exampleCreateTaskAssistConversationResponse)
            whenever(chatSessionStorage.getSession(any(), any())).thenReturn(spySession)
            whenever(spySession.sessionState).thenReturn(
                PrepareCodeGenerationState(
                    testTabId,
                    CancellationTokenSource(),
                    "",
                    mock(),
                    newFileContents,
                    deletedFiles,
                    testReferences,
                    testUploadId,
                    0,
                    messenger,
                    diffMetricsProcessed = DiffMetricsProcessed(HashSet(), HashSet()),
                ),
            )

            controller.processFileClicked(message)

            val newFileContentsCopy = newFileContents.toMutableList()
            newFileContentsCopy[0] = newFileContentsCopy[0].copy()
            newFileContentsCopy[0].rejected = true
            newFileContentsCopy[0].changeApplied = false
            coVerify { messenger.updateFileComponent(testTabId, newFileContentsCopy, deletedFiles, "") }
        }

    @Test
    fun `test processFileClicked handles file acceptance`() =
        runTest {
            val featureDevService = mockk<FeatureDevService>()
            val repoContext = mock<FeatureDevSessionContext>()
            val sessionStateConfig = SessionStateConfig(testConversationId, repoContext, featureDevService)

            whenever(featureDevClient.createTaskAssistConversation()).thenReturn(exampleCreateTaskAssistConversationResponse)
            whenever(chatSessionStorage.getSession(any(), any())).thenReturn(spySession)
            whenever(spySession.sessionState).thenReturn(
                PrepareCodeGenerationState(
                    testTabId,
                    CancellationTokenSource(),
                    "",
                    sessionStateConfig,
                    newFileContents,
                    deletedFiles,
                    testReferences,
                    testUploadId,
                    0,
                    messenger,
                    diffMetricsProcessed = DiffMetricsProcessed(HashSet(), HashSet()),
                ),
            )
            doReturn(testConversationId).`when`(spySession).conversationId
            doReturn(Unit).`when`(spySession).insertChanges(any(), any(), any())

            mockkObject(AmazonqTelemetry)
            every {
                AmazonqTelemetry.isAcceptedCodeChanges(
                    amazonqNumberOfFilesAccepted = 1.0,
                    amazonqConversationId = testConversationId,
                    enabled = true,
                    credentialStartUrl = any()
                )
            } just runs

            // Accept first file:
            controller.processFileClicked(IncomingFeatureDevMessage.FileClicked(testTabId, newFileContents[0].zipFilePath, "", "accept-change"))

            val newFileContentsCopy = newFileContents.toList()
            newFileContentsCopy[0].rejected = false
            newFileContentsCopy[0].changeApplied = true
            coVerify { messenger.updateFileComponent(testTabId, newFileContents, deletedFiles, "") }

            mockitoVerify(
                spySession,
                times(1),
            ).insertChanges(listOf(newFileContents[0]), listOf(), testReferences)

            // Does not continue automatically, because files are remaining:
            mockitoVerify(
                controller,
                times(0),
            ).insertCode(testTabId)
        }

    @Test
    fun `test processFileClicked automatically continues when last file is accepted`() =
        runTest {
            val featureDevService = mockk<FeatureDevService>()
            val repoContext = mock<FeatureDevSessionContext>()
            val sessionStateConfig = SessionStateConfig(testConversationId, repoContext, featureDevService)

            whenever(featureDevClient.createTaskAssistConversation()).thenReturn(exampleCreateTaskAssistConversationResponse)
            whenever(chatSessionStorage.getSession(any(), any())).thenReturn(spySession)
            whenever(spySession.sessionState).thenReturn(
                PrepareCodeGenerationState(
                    testTabId,
                    CancellationTokenSource(),
                    "",
                    sessionStateConfig,
                    newFileContents,
                    deletedFiles,
                    testReferences,
                    testUploadId,
                    0,
                    messenger,
                    diffMetricsProcessed = DiffMetricsProcessed(HashSet(), HashSet()),
                ),
            )
            doReturn(testConversationId).`when`(spySession).conversationId
            doReturn(Unit).`when`(spySession).insertChanges(any(), any(), any())

            mockkObject(AmazonqTelemetry)
            every {
                AmazonqTelemetry.isAcceptedCodeChanges(
                    amazonqNumberOfFilesAccepted = 1.0,
                    amazonqConversationId = testConversationId,
                    enabled = true,
                    credentialStartUrl = any()
                )
            } just runs

            val newFileContentsCopy = newFileContents.toList()
            newFileContentsCopy[0].rejected = false
            newFileContentsCopy[0].changeApplied = true
            newFileContentsCopy[1].rejected = false
            newFileContentsCopy[1].changeApplied = true
            deletedFiles[0].rejected = false
            deletedFiles[0].changeApplied = true

            // This is simulating the file already being an accepted state, and accept-change being called redundantly. This is necessary because of the test
            // setup, which should be fixed to avoid heavy-handed mocking of the session state (so that we can see the session state be incrementally updated).
            deletedFiles[1].rejected = false
            deletedFiles[1].changeApplied = true

            // When the last file is accepted:
            controller.processFileClicked(IncomingFeatureDevMessage.FileClicked(testTabId, deletedFiles[1].zipFilePath, "", "accept-change"))

            // We auto-continue to the next step with a noop insertCode call:
            mockitoVerify(
                controller,
                times(1),
            ).insertCode(testTabId)
        }

    @Test
    fun `test modifyDefaultSourceFolder customer does not select a folder`() =
        runTest {
            val followUp = FollowUp(FollowUpTypes.MODIFY_DEFAULT_SOURCE_FOLDER, pillText = "Modify default source folder")
            val message = IncomingFeatureDevMessage.FollowupClicked(followUp, testTabId, "", "test-command")

            whenever(featureDevClient.createTaskAssistConversation()).thenReturn(exampleCreateTaskAssistConversationResponse)
            whenever(featureDevClient.sendFeatureDevTelemetryEvent(any())).thenReturn(exampleSendTelemetryEventResponse)
            whenever(chatSessionStorage.getSession(any(), any())).thenReturn(spySession)

            mockkStatic("software.aws.toolkits.jetbrains.common.util.FileUtilsKt")
            every { selectFolder(any(), any()) } returns null

            spySession.preloader(messenger)
            controller.processFollowupClickedMessage(message)

            coVerifyOrder {
                messenger.sendSystemPrompt(
                    tabId = testTabId,
                    followUp =
                    listOf(
                        FollowUp(
                            pillText = message("amazonqFeatureDev.follow_up.modify_source_folder"),
                            type = FollowUpTypes.MODIFY_DEFAULT_SOURCE_FOLDER,
                            status = FollowUpStatusType.Info,
                        ),
                    ),
                )
            }
        }

    @Test
    fun `test modifyDefaultSourceFolder customer selects a folder outside the workspace`() =
        runTest {
            val followUp = FollowUp(FollowUpTypes.MODIFY_DEFAULT_SOURCE_FOLDER, pillText = "Modify default source folder")
            val message = IncomingFeatureDevMessage.FollowupClicked(followUp, testTabId, "", "test-command")

            whenever(featureDevClient.createTaskAssistConversation()).thenReturn(exampleCreateTaskAssistConversationResponse)
            whenever(featureDevClient.sendFeatureDevTelemetryEvent(any())).thenReturn(exampleSendTelemetryEventResponse)
            whenever(chatSessionStorage.getSession(any(), any())).thenReturn(spySession)

            mockkStatic("software.aws.toolkits.jetbrains.common.util.FileUtilsKt")
            every { selectFolder(any(), any()) } returns LightVirtualFile("/path")

            spySession.preloader(messenger)
            controller.processFollowupClickedMessage(message)

            coVerifyOrder {
                messenger.sendAnswer(
                    tabId = testTabId,
                    messageType = FeatureDevMessageType.Answer,
                    message = message("amazonqFeatureDev.follow_up.incorrect_source_folder"),
                )
                messenger.sendSystemPrompt(
                    tabId = testTabId,
                    followUp =
                    listOf(
                        FollowUp(
                            pillText = message("amazonqFeatureDev.follow_up.modify_source_folder"),
                            type = FollowUpTypes.MODIFY_DEFAULT_SOURCE_FOLDER,
                            status = FollowUpStatusType.Info,
                        ),
                    ),
                )
            }
        }

    @Test
    fun `test modifyDefaultSourceFolder customer selects a correct sub folder`() =
        runTest {
            val followUp = FollowUp(FollowUpTypes.MODIFY_DEFAULT_SOURCE_FOLDER, pillText = "Modify default source folder")
            val message = IncomingFeatureDevMessage.FollowupClicked(followUp, testTabId, "", "test-command")

            whenever(featureDevClient.createTaskAssistConversation()).thenReturn(exampleCreateTaskAssistConversationResponse)
            whenever(featureDevClient.sendFeatureDevTelemetryEvent(any())).thenReturn(exampleSendTelemetryEventResponse)
            whenever(chatSessionStorage.getSession(any(), any())).thenReturn(spySession)

            val folder = LightVirtualFile("${spySession.context.workspaceRoot.path.removePrefix("/")}/path/to/sub/folder")
            mockkStatic("software.aws.toolkits.jetbrains.common.util.FileUtilsKt")
            every { selectFolder(any(), any()) } returns folder

            spySession.preloader(messenger)
            controller.processFollowupClickedMessage(message)

            coVerify {
                messenger.sendAnswer(
                    tabId = testTabId,
                    messageType = FeatureDevMessageType.Answer,
                    message = message("amazonqFeatureDev.follow_up.modified_source_folder", folder.path),
                    canBeVoted = true,
                )
            }
        }
}
