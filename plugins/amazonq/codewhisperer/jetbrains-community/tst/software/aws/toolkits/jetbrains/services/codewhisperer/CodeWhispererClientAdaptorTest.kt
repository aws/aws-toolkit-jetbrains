// Copyright 2022 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.codewhisperer

import com.intellij.openapi.util.SystemInfo
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.RuleChain
import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.stub
import org.mockito.kotlin.verify
import software.amazon.awssdk.services.codewhispererruntime.CodeWhispererRuntimeClient
import software.amazon.awssdk.services.codewhispererruntime.model.ArtifactType
import software.amazon.awssdk.services.codewhispererruntime.model.CodeAnalysisFindingsSchema
import software.amazon.awssdk.services.codewhispererruntime.model.CodeAnalysisStatus
import software.amazon.awssdk.services.codewhispererruntime.model.CreateUploadUrlRequest
import software.amazon.awssdk.services.codewhispererruntime.model.CreateUploadUrlResponse
import software.amazon.awssdk.services.codewhispererruntime.model.GenerateCompletionsRequest
import software.amazon.awssdk.services.codewhispererruntime.model.GetCodeAnalysisRequest
import software.amazon.awssdk.services.codewhispererruntime.model.GetCodeAnalysisResponse
import software.amazon.awssdk.services.codewhispererruntime.model.IdeCategory
import software.amazon.awssdk.services.codewhispererruntime.model.ListCodeAnalysisFindingsRequest
import software.amazon.awssdk.services.codewhispererruntime.model.ListCodeAnalysisFindingsResponse
import software.amazon.awssdk.services.codewhispererruntime.model.ListFeatureEvaluationsRequest
import software.amazon.awssdk.services.codewhispererruntime.model.ListFeatureEvaluationsResponse
import software.amazon.awssdk.services.codewhispererruntime.model.OperatingSystem
import software.amazon.awssdk.services.codewhispererruntime.model.OptOutPreference
import software.amazon.awssdk.services.codewhispererruntime.model.ProgrammingLanguage
import software.amazon.awssdk.services.codewhispererruntime.model.SendTelemetryEventRequest
import software.amazon.awssdk.services.codewhispererruntime.model.SendTelemetryEventResponse
import software.amazon.awssdk.services.codewhispererruntime.model.StartCodeAnalysisRequest
import software.amazon.awssdk.services.codewhispererruntime.model.StartCodeAnalysisResponse
import software.amazon.awssdk.services.codewhispererruntime.paginators.GenerateCompletionsIterable
import software.amazon.awssdk.services.ssooidc.SsoOidcClient
import software.amazon.q.core.utils.test.aString
import software.amazon.q.jetbrains.core.MockClientManagerRule
import software.amazon.q.jetbrains.core.credentials.AwsBearerTokenConnection
import software.amazon.q.jetbrains.core.credentials.ManagedSsoProfile
import software.amazon.q.jetbrains.core.credentials.MockCredentialManagerRule
import software.amazon.q.jetbrains.core.credentials.MockToolkitAuthManagerRule
import software.amazon.q.jetbrains.core.credentials.ToolkitConnectionManager
import software.amazon.q.jetbrains.core.credentials.logoutFromSsoConnection
import software.amazon.q.jetbrains.core.credentials.pinning.QConnection
import software.amazon.q.jetbrains.core.credentials.sono.Q_SCOPES
import software.amazon.q.jetbrains.core.credentials.sono.SONO_REGION
import software.aws.toolkits.jetbrains.services.amazonq.FEATURE_EVALUATION_PRODUCT_NAME
import software.aws.toolkits.jetbrains.services.codewhisperer.CodeWhispererTestUtil.metadata
import software.aws.toolkits.jetbrains.services.codewhisperer.CodeWhispererTestUtil.sdkHttpResponse
import software.aws.toolkits.jetbrains.services.codewhisperer.credentials.CodeWhispererClientAdaptorImpl
import software.aws.toolkits.jetbrains.services.codewhisperer.util.CodeWhispererConstants
import software.amazon.q.jetbrains.settings.AwsSettings
import software.amazon.q.jetbrains.utils.rules.JavaCodeInsightTestFixtureRule

class CodeWhispererClientAdaptorTest {
    val projectRule = JavaCodeInsightTestFixtureRule()
    val disposableRule = DisposableRule()
    val mockClientManagerRule = MockClientManagerRule()
    val mockCredentialRule = MockCredentialManagerRule()
    val authManagerRule = MockToolkitAuthManagerRule()

    @Rule
    @JvmField
    val ruleChain = RuleChain(projectRule, mockCredentialRule, mockClientManagerRule, authManagerRule, disposableRule)

    private lateinit var bearerClient: CodeWhispererRuntimeClient
    private lateinit var ssoClient: SsoOidcClient

    private lateinit var sut: CodeWhispererClientAdaptorImpl
    private var isTelemetryEnabledDefault: Boolean = false

    @Before
    fun setup() {
        sut = CodeWhispererClientAdaptorImpl(projectRule.project)
        ssoClient = mockClientManagerRule.create()

        bearerClient = mockClientManagerRule.create<CodeWhispererRuntimeClient>().stub {
            on { generateCompletionsPaginator(any<GenerateCompletionsRequest>()) } doReturn generateCompletionsPaginatorResponse
            on { createUploadUrl(any<CreateUploadUrlRequest>()) } doReturn createUploadUrlResponse
            on { startCodeAnalysis(any<StartCodeAnalysisRequest>()) } doReturn startCodeAnalysisResponse
            on { getCodeAnalysis(any<GetCodeAnalysisRequest>()) } doReturn getCodeAnalysisResponse
            on { listCodeAnalysisFindings(any<ListCodeAnalysisFindingsRequest>()) } doReturn listCodeAnalysisFindingsResponse
            on { sendTelemetryEvent(any<SendTelemetryEventRequest>()) } doReturn sendtelemetryEventResponse
            on { listFeatureEvaluations(any<ListFeatureEvaluationsRequest>()) } doReturn listFeatureEvaluationsResponse
        }

        val conn = authManagerRule.createConnection(ManagedSsoProfile("us-east-1", "url", Q_SCOPES))
        ToolkitConnectionManager.getInstance(projectRule.project).switchConnection(conn)

        isTelemetryEnabledDefault = AwsSettings.getInstance().isTelemetryEnabled
    }

    @After
    fun tearDown() {
        AwsSettings.getInstance().isTelemetryEnabled = isTelemetryEnabledDefault
    }

    @Test
    fun `Sono region is us-east-1`() {
        assertThat("us-east-1").isEqualTo(SONO_REGION)
    }

    @Test
    fun `should throw if there is no valid credential, otherwise return codewhispererRuntimeClient`() {
        val connectionManager = ToolkitConnectionManager.getInstance(projectRule.project)

        assertThat(connectionManager.activeConnectionForFeature(QConnection.getInstance()))
            .isNotNull
        assertThat(sut.bearerClient())
            .isNotNull
            .isInstanceOf(CodeWhispererRuntimeClient::class.java)

        logoutFromSsoConnection(projectRule.project, connectionManager.activeConnectionForFeature(QConnection.getInstance()) as AwsBearerTokenConnection)
        assertThat(connectionManager.activeConnectionForFeature(QConnection.getInstance())).isNull()
        assertThrows<Exception>("attempt to get bearer client while there is no valid credential") {
            sut.bearerClient()
        }

        val anotherQConnection = authManagerRule.createConnection(ManagedSsoProfile("us-east-1", aString(), Q_SCOPES))
        connectionManager.switchConnection(anotherQConnection)
        assertThat(connectionManager.activeConnectionForFeature(QConnection.getInstance()))
            .isNotNull
            .isEqualTo(anotherQConnection)
        assertThat(sut.bearerClient())
            .isNotNull
            .isInstanceOf(CodeWhispererRuntimeClient::class.java)
    }

    @Test
    fun `createUploadUrl - bearer`() {
        val actual = sut.createUploadUrl(createUploadUrlRequest)

        argumentCaptor<CreateUploadUrlRequest>().apply {
            verify(bearerClient).createUploadUrl(capture())
            assertThat(actual).isInstanceOf(CreateUploadUrlResponse::class.java)
            assertThat(actual).usingRecursiveComparison()
                .comparingOnlyFields("uploadUrl", "uploadId")
                .isEqualTo(createUploadUrlResponse)
        }
    }

    @Test
    fun `createCodeScan - bearer`() {
        val actual = sut.createCodeScan(createCodeScanRequest)

        argumentCaptor<StartCodeAnalysisRequest>().apply {
            verify(bearerClient).startCodeAnalysis(capture())
            assertThat(actual).isInstanceOf(StartCodeAnalysisResponse::class.java)
            assertThat(actual).usingRecursiveComparison()
                .comparingOnlyFields("jobId", "status", "errorMessage")
                .isEqualTo(startCodeAnalysisResponse)
        }
    }

    @Test
    fun `getCodeScan - bearer`() {
        val actual = sut.getCodeScan(getCodeScanRequest)

        argumentCaptor<GetCodeAnalysisRequest>().apply {
            verify(bearerClient).getCodeAnalysis(capture())
            assertThat(actual).isInstanceOf(GetCodeAnalysisResponse::class.java)
            assertThat(actual).usingRecursiveComparison()
                .comparingOnlyFields("status", "errorMessage")
                .isEqualTo(getCodeAnalysisResponse)
        }
    }

    @Test
    fun `listCodeScanFindings - bearer`() {
        val actual = sut.listCodeScanFindings(listCodeScanFindingsRequest)

        argumentCaptor<ListCodeAnalysisFindingsRequest>().apply {
            verify(bearerClient).listCodeAnalysisFindings(capture())
            assertThat(actual).isInstanceOf(ListCodeAnalysisFindingsResponse::class.java)
            assertThat(actual.codeAnalysisFindings()).isEqualTo(listCodeAnalysisFindingsResponse.codeAnalysisFindings())
            assertThat(actual.nextToken()).isEqualTo(listCodeAnalysisFindingsResponse.nextToken())
        }
    }

    @Test
    fun `sendTelemetryEvent for codePercentage respects telemetry optin status`() {
        sendTelemetryEventOptOutCheckHelper {
            sut.sendCodePercentageTelemetry(aProgrammingLanguage(), aString(), 0, 1, 0, 0, 0)
        }
    }

    @Test
    fun `sendTelemetryEvent for codeScan respects telemetry optin status`() {
        sendTelemetryEventOptOutCheckHelper {
            sut.sendCodeScanTelemetry(aProgrammingLanguage(), null, CodeWhispererConstants.CodeAnalysisScope.PROJECT)
        }
    }

    @Test
    fun `sendTelemetryEvent for userModification respects telemetry optin status`() {
        sendTelemetryEventOptOutCheckHelper {
            sut.sendUserModificationTelemetry(aString(), aString(), aProgrammingLanguage(), aString(), 0, 0)
        }
    }

    @Test
    fun `test listFeatureEvaluations sends expected payloads`() {
        sut.listFeatureEvaluations()

        verify(bearerClient).listFeatureEvaluations(
            argThat<ListFeatureEvaluationsRequest> {
                this.userContext().ideCategory() == IdeCategory.JETBRAINS &&
                    this.userContext().operatingSystem() == when {
                        SystemInfo.isWindows -> OperatingSystem.WINDOWS
                        SystemInfo.isMac -> OperatingSystem.MAC
                        else -> OperatingSystem.LINUX
                    } &&
                    this.userContext().product() == FEATURE_EVALUATION_PRODUCT_NAME
            }
        )
    }

    private fun sendTelemetryEventOptOutCheckHelper(mockApiCall: () -> Unit) {
        AwsSettings.getInstance().isTelemetryEnabled = true
        mockApiCall()
        verify(bearerClient).sendTelemetryEvent(
            argThat<SendTelemetryEventRequest> { optOutPreference() == OptOutPreference.OPTIN }
        )

        AwsSettings.getInstance().isTelemetryEnabled = false
        mockApiCall()
        verify(bearerClient).sendTelemetryEvent(
            argThat<SendTelemetryEventRequest> { optOutPreference() == OptOutPreference.OPTOUT }
        )
    }

    private companion object {
        val createCodeScanRequest = StartCodeAnalysisRequest.builder()
            .artifacts(mapOf(ArtifactType.SOURCE_CODE to "foo"))
            .clientToken("token")
            .programmingLanguage(
                ProgrammingLanguage.builder()
                    .languageName("python")
                    .build()
            ).scope("PROJECT")
            .build()

        val createUploadUrlRequest = CreateUploadUrlRequest.builder()
            .contentMd5("foo")
            .artifactType(software.amazon.awssdk.services.codewhispererruntime.model.ArtifactType.SOURCE_CODE)
            .build()

        val getCodeScanRequest = GetCodeAnalysisRequest.builder()
            .jobId("jobid")
            .build()

        val listCodeScanFindingsRequest = ListCodeAnalysisFindingsRequest.builder()
            .codeAnalysisFindingsSchema(CodeAnalysisFindingsSchema.CODEANALYSIS_FINDINGS_1_0)
            .jobId("listCodeScanFindings - JobId")
            .nextToken("nextToken")
            .build()

        val createUploadUrlResponse: CreateUploadUrlResponse = CreateUploadUrlResponse.builder()
            .uploadUrl("url")
            .uploadId("id")
            .responseMetadata(metadata)
            .sdkHttpResponse(sdkHttpResponse)
            .build() as CreateUploadUrlResponse

        val startCodeAnalysisResponse = StartCodeAnalysisResponse.builder()
            .jobId("create-code-scan-user")
            .status(CodeAnalysisStatus.COMPLETED)
            .errorMessage("message")
            .responseMetadata(metadata)
            .sdkHttpResponse(sdkHttpResponse)
            .build() as StartCodeAnalysisResponse

        val getCodeAnalysisResponse = GetCodeAnalysisResponse.builder()
            .status(CodeAnalysisStatus.PENDING)
            .errorMessage("message")
            .responseMetadata(metadata)
            .sdkHttpResponse(sdkHttpResponse)
            .build() as GetCodeAnalysisResponse

        val listCodeAnalysisFindingsResponse = ListCodeAnalysisFindingsResponse.builder()
            .codeAnalysisFindings("findings")
            .nextToken("nextToken")
            .responseMetadata(metadata)
            .sdkHttpResponse(sdkHttpResponse)
            .build() as ListCodeAnalysisFindingsResponse

        val sendtelemetryEventResponse = SendTelemetryEventResponse.builder().build()

        val listFeatureEvaluationsResponse = ListFeatureEvaluationsResponse.builder().build()

        private val generateCompletionsPaginatorResponse: GenerateCompletionsIterable = mock()
    }
}
