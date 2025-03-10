// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonqFeatureDev.session

import com.intellij.testFramework.RuleChain
import com.intellij.testFramework.replaceService
import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.runs
import io.mockk.spyk
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import software.aws.toolkits.jetbrains.services.amazonq.FeatureDevSessionContext
import software.aws.toolkits.jetbrains.services.amazonq.ZipCreationResult
import software.aws.toolkits.jetbrains.services.amazonq.messages.MessagePublisher
import software.aws.toolkits.jetbrains.services.amazonqFeatureDev.FeatureDevTestBase
import software.aws.toolkits.jetbrains.services.amazonqFeatureDev.clients.FeatureDevClient
import software.aws.toolkits.jetbrains.services.amazonqFeatureDev.messages.sendAnswerPart
import software.aws.toolkits.jetbrains.services.amazonqFeatureDev.messages.sendUpdatePlaceholder
import software.aws.toolkits.jetbrains.services.amazonqFeatureDev.util.FeatureDevService
import software.aws.toolkits.jetbrains.services.amazonqFeatureDev.util.deleteUploadArtifact
import software.aws.toolkits.jetbrains.services.amazonqFeatureDev.util.uploadArtifactToS3
import software.aws.toolkits.telemetry.AmazonqTelemetry
import java.io.File

class SessionStateChangeTest : FeatureDevTestBase() {
    @Rule
    @JvmField
    val ruleChain = RuleChain(projectRule, disposableRule)

    private lateinit var session: Session
    private lateinit var sessionStateConfig: SessionStateConfig
    private lateinit var repoContext: FeatureDevSessionContext
    private lateinit var messenger: MessagePublisher
    private lateinit var featureDevService: FeatureDevService
    private lateinit var featureDevClient: FeatureDevClient

    @Before
    override fun setup() {
        messenger = mock()
        featureDevClient = mock()
        featureDevService = mockk()
        repoContext = mock()
        sessionStateConfig = SessionStateConfig(testConversationId, repoContext, featureDevService)
        every { featureDevService.proxyClient } returns featureDevClient
        every { featureDevService.project } returns projectRule.project

        projectRule.project.replaceService(FeatureDevService::class.java, featureDevService, disposableRule.disposable)
        projectRule.project.replaceService(FeatureDevClient::class.java, featureDevClient, disposableRule.disposable)
        projectRule.project.replaceService(SessionStateConfig::class.java, sessionStateConfig, disposableRule.disposable)
        session = Session("tabId", projectRule.project)

        var field = Session::class.java.getDeclaredField("featureDevService")
        field.isAccessible = true
        field.set(session, featureDevService)

        mockkStatic("software.aws.toolkits.jetbrains.services.amazonqFeatureDev.util.UploadArtifactKt")
        every { uploadArtifactToS3(any(), any(), any(), any(), any()) } just runs
        every { deleteUploadArtifact(any()) } just runs
    }

    @After
    fun clear() {
        unmockkAll()
    }

    @Test
    fun `test complete successful workflow`() = runTest {
        // Spy on the session to allow mocking private methods
        val sessionSpy = spyk(session, recordPrivateCalls = true)

        // Start from ConversationNotStartedState
        assertThat(sessionSpy.sessionState).isInstanceOf(ConversationNotStartedState::class.java)
        assertThat(sessionSpy.isAuthenticating).isFalse()
        assertThat(sessionSpy.sessionState.phase).isEqualTo(SessionStatePhase.INIT)

        // Mocking FeatureDevClient methods
        whenever(featureDevClient.createTaskAssistConversation()).thenReturn(exampleCreateTaskAssistConversationResponse)
        whenever(featureDevClient.sendFeatureDevTelemetryEvent(testConversationId)).thenReturn(exampleSendTelemetryEventResponse)

        // Mocking FeatureDevService methods
        every { featureDevService.createConversation() } returns testConversationId
        every { featureDevService.startTaskAssistCodeGeneration(any(), any(), any(), any(), any()) } returns exampleStartTaskAssistConversationResponse
        every { featureDevService.getTaskAssistCodeGeneration(any(), any()) } returns exampleCompleteGetTaskAssistCodeGenerationResponse
        coEvery { featureDevService.exportTaskAssistArchiveResult(any()) } returns exampleExportTaskAssistResultArchiveResponse
        every { featureDevService.sendFeatureDevEvent(any()) } just runs

        // Mocking AmazonQTelemetry
        mockkObject(AmazonqTelemetry)
        every {
            AmazonqTelemetry.createUpload(
                amazonqConversationId = any(),
                amazonqRepositorySize = any(),
                amazonqUploadIntent = any(),
                result = any(),
                reason = any(),
                reasonDesc = any(),
                duration = any(),
                credentialStartUrl = any()
            )
        } just runs

        // Mock the private function
        every { sessionSpy["getSessionStateConfig"]() } returns sessionStateConfig

        // Mocking MessagePublisher
        mockkStatic(MessagePublisher::sendAnswerPart)
        coEvery { messenger.sendAnswerPart(any(), any()) } just runs
        coEvery { messenger.sendUpdatePlaceholder(any(), any()) } just runs

        // Preloading session to transition to PrepareCodeGenerationState
        sessionSpy.preloader("Test Message", messenger)
        assertThat(sessionSpy.sessionState).isInstanceOf(PrepareCodeGenerationState::class.java)

        // Simulating code generation and transitioning back to PrepareCodeGenerationState
        val mockFile: File = mock()
        val repoZipResult = ZipCreationResult(mockFile, testChecksumSha, testContentLength)
        val action = SessionStateAction("test-task", "Test Message")

        whenever(repoContext.getProjectZip()).thenReturn(repoZipResult)
        every { featureDevService.createUploadUrl(any(), any(), any(), any()) } returns exampleCreateUploadUrlResponse

        val prepareState = sessionSpy.sessionState as PrepareCodeGenerationState
        assertThat(prepareState.currentIteration).isEqualTo(1)

        val actual = prepareState.interact(action)
        assertThat(actual.nextState).isInstanceOf(PrepareCodeGenerationState::class.java)
        val nextState = actual.nextState as PrepareCodeGenerationState

        verify(exactly = 1) { featureDevService.createConversation() }
        assertThat(nextState.phase).isEqualTo(SessionStatePhase.CODEGEN)
        assertThat(nextState.codeGenerationRemainingIterationCount).isEqualTo(2)
        assertThat(nextState.currentIteration).isEqualTo(1)
        assertThat(nextState.codeGenerationTotalIterationCount).isEqualTo(3)
        assertThat(actual.interaction.interactionSucceeded).isEqualTo(true)
        assertThat(actual.interaction.content).isEqualTo("")
    }
}
