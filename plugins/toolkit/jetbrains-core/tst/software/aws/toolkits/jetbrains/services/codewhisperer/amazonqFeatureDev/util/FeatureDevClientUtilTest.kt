// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.codewhisperer.amazonqFeatureDev.util

import com.intellij.openapi.application.ApplicationManager
import com.intellij.testFramework.RuleChain
import com.intellij.testFramework.replaceService
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import software.amazon.awssdk.awscore.exception.AwsErrorDetails
import software.amazon.awssdk.services.codewhispererruntime.model.CreateUploadUrlResponse
import software.amazon.awssdk.services.codewhispererruntime.model.ValidationException
import software.amazon.awssdk.services.codewhispererstreaming.model.ThrottlingException
import software.aws.toolkits.core.telemetry.TelemetryBatcher
import software.aws.toolkits.core.telemetry.TelemetryPublisher
import software.aws.toolkits.jetbrains.services.amazonqFeatureDev.ContentLengthError
import software.aws.toolkits.jetbrains.services.amazonqFeatureDev.PlanIterationLimitError
import software.aws.toolkits.jetbrains.services.amazonqFeatureDev.clients.FeatureDevClient
import software.aws.toolkits.jetbrains.services.amazonqFeatureDev.clients.GenerateTaskAssistPlanResult
import software.aws.toolkits.jetbrains.services.amazonqFeatureDev.util.createConversation
import software.aws.toolkits.jetbrains.services.amazonqFeatureDev.util.createUploadUrl
import software.aws.toolkits.jetbrains.services.amazonqFeatureDev.util.generatePlan
import software.aws.toolkits.jetbrains.services.codewhisperer.CodeWhispererTestUtil
import software.aws.toolkits.jetbrains.services.codewhisperer.amazonqFeatureDev.FeatureDevTestBase
import software.aws.toolkits.jetbrains.services.telemetry.NoOpPublisher
import software.aws.toolkits.jetbrains.services.telemetry.TelemetryService
import software.aws.toolkits.resources.message

class FeatureDevClientUtilTest : FeatureDevTestBase() {
    @Rule
    @JvmField
    val ruleChain = RuleChain(projectRule, disposableRule)

    private class TestTelemetryService(
        publisher: TelemetryPublisher = NoOpPublisher(),
        batcher: TelemetryBatcher
    ) : TelemetryService(publisher, batcher)

    private lateinit var featureDevClient: FeatureDevClient
    private lateinit var telemetryService: TelemetryService
    private lateinit var batcher: TelemetryBatcher

    private val uploadId = "test-upload-id"

    @Before
    override fun setup() {
        featureDevClient = mock()
        batcher = mock<TelemetryBatcher>()
        telemetryService = TestTelemetryService(batcher = batcher)
        ApplicationManager.getApplication().replaceService(TelemetryService::class.java, telemetryService, disposableRule.disposable)
    }

    @Test
    fun `test createConversation`() {
        whenever(featureDevClient.createTaskAssistConversation()).thenReturn(exampleCreateTaskAssistConversationResponse)
        val actual = createConversation(featureDevClient)
        assertThat(actual).isEqualTo(exampleCreateTaskAssistConversationResponse.conversationId())
    }

    @Test
    fun `test createConversation with error`() {
        whenever(featureDevClient.createTaskAssistConversation()).thenThrow(RuntimeException())
        assertThatThrownBy {
            createConversation(featureDevClient)
        }.isInstanceOf(RuntimeException::class.java)
    }

    @Test
    fun `test createUploadUrl`() {
        whenever(featureDevClient.createTaskAssistUploadUrl(testConversationId, testChecksumSha, testContentLength)).thenReturn(exampleCreateUploadUrlResponse)

        val actual = createUploadUrl(featureDevClient, testConversationId, testChecksumSha, testContentLength)
        assertThat(actual).isInstanceOf(CreateUploadUrlResponse::class.java)
        assertThat(actual).usingRecursiveComparison().comparingOnlyFields("uploadUrl", "uploadId", "kmsKeyArn")
            .isEqualTo(exampleCreateUploadUrlResponse)
    }

    @Test
    fun `test createUploadUrl with error`() {
        whenever(featureDevClient.createTaskAssistUploadUrl(testConversationId, testChecksumSha, testContentLength)).thenThrow(RuntimeException())
        assertThatThrownBy {
            createUploadUrl(featureDevClient, testConversationId, testChecksumSha, testContentLength)
        }.isInstanceOf(RuntimeException::class.java)
    }

    @Test
    fun `test createUploadUrl with validation error`() {
        whenever(featureDevClient.createTaskAssistUploadUrl(testConversationId, testChecksumSha, testContentLength)).thenThrow(
            ValidationException.builder()
                .requestId(CodeWhispererTestUtil.testRequestId)
                .message("Invalid contentLength")
                .awsErrorDetails(AwsErrorDetails.builder().errorMessage("Invalid contentLength").build())
                .build()
        )

        assertThatThrownBy {
            createUploadUrl(featureDevClient, testConversationId, testChecksumSha, testContentLength)
        }.isInstanceOf(ContentLengthError::class.java).hasMessage(message("amazonqFeatureDev.content_length.error_text"))
    }

    @Test
    fun `test generatePlan`() = runTest {
        whenever(featureDevClient.generateTaskAssistPlan(testConversationId, uploadId, userMessage)).thenReturn(exampleGenerateTaskAssistPlanResult)

        val actual = generatePlan(featureDevClient, testConversationId, uploadId, userMessage, 0)
        assertThat(actual).isInstanceOf(GenerateTaskAssistPlanResult::class.java)
        assertThat(actual.approach).isEqualTo("Generated approach for plan")
    }

    @Test(expected = RuntimeException::class)
    fun `test generatePlan with error`() = runTest {
        whenever(featureDevClient.generateTaskAssistPlan(testConversationId, uploadId, userMessage)).thenThrow(RuntimeException())

        generatePlan(featureDevClient, testConversationId, uploadId, userMessage, 0)
    }

    @Test
    fun `test generatePlan with throttling error`() = runTest {
        whenever(featureDevClient.generateTaskAssistPlan(testConversationId, uploadId, userMessage)).thenThrow(
            ThrottlingException.builder()
                .requestId(CodeWhispererTestUtil.testRequestId)
                .message("limit for number of iterations on an implementation plan")
                .awsErrorDetails(AwsErrorDetails.builder().errorMessage("Plan Iteration limit").build())
                .build()
        )
        var caughtException: Throwable? = null
        try {
            generatePlan(featureDevClient, testConversationId, uploadId, userMessage, 0)
        } catch (e: Throwable) {
            caughtException = e
        }
        assertThat(caughtException is PlanIterationLimitError).isTrue()
        assertThat(caughtException).hasMessage(message("amazonqFeatureDev.approach_gen.iteration_limit.error_text"))
    }
}
