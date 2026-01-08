// Copyright 2022 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.codewhisperer.codescan

import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import com.github.tomakehurst.wiremock.junit.WireMockRule
import com.intellij.analysis.problemsView.toolWindow.ProblemsView
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.RegisterToolWindowTask
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.replaceService
import com.intellij.util.io.systemIndependentPath
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.jupiter.api.assertInstanceOf
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.doNothing
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.isNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.spy
import org.mockito.kotlin.stub
import org.mockito.kotlin.whenever
import software.amazon.awssdk.awscore.DefaultAwsResponseMetadata
import software.amazon.awssdk.awscore.util.AwsHeader
import software.amazon.awssdk.services.codewhispererruntime.model.CodeAnalysisStatus
import software.amazon.awssdk.services.codewhispererruntime.model.CodeFixJobStatus
import software.amazon.awssdk.services.codewhispererruntime.model.CreateUploadUrlResponse
import software.amazon.awssdk.services.codewhispererruntime.model.GetCodeAnalysisResponse
import software.amazon.awssdk.services.codewhispererruntime.model.GetCodeFixJobResponse
import software.amazon.awssdk.services.codewhispererruntime.model.ListCodeAnalysisFindingsResponse
import software.amazon.awssdk.services.codewhispererruntime.model.Reference
import software.amazon.awssdk.services.codewhispererruntime.model.Span
import software.amazon.awssdk.services.codewhispererruntime.model.StartCodeAnalysisResponse
import software.amazon.awssdk.services.codewhispererruntime.model.StartCodeFixJobResponse
import software.amazon.q.jetbrains.core.MockClientManagerRule
import software.amazon.q.jetbrains.utils.rules.CodeInsightTestFixtureRule
import software.aws.toolkits.jetbrains.services.codewhisperer.CodeWhispererTestUtil
import software.aws.toolkits.jetbrains.services.codewhisperer.codescan.sessionconfig.CodeScanSessionConfig
import software.aws.toolkits.jetbrains.services.codewhisperer.credentials.CodeWhispererClientAdaptor
import software.aws.toolkits.jetbrains.services.codewhisperer.credentials.CodeWhispererLoginType
import software.aws.toolkits.jetbrains.services.codewhisperer.explorer.CodeWhispererExplorerActionManager
import software.aws.toolkits.jetbrains.services.codewhisperer.util.CodeWhispererConstants
import software.aws.toolkits.jetbrains.services.codewhisperer.util.CodeWhispererZipUploadManager
import software.aws.toolkits.telemetry.CodewhispererLanguage
import java.nio.file.Path
import kotlin.test.assertNotNull

open class CodeWhispererCodeScanTestBase(projectRule: CodeInsightTestFixtureRule) {
    @Rule
    @JvmField
    val applicationRule = ApplicationRule()

    @Rule
    @JvmField
    val projectRule: CodeInsightTestFixtureRule = projectRule

    @Rule
    @JvmField
    val disposableRule = DisposableRule()

    @Rule
    @JvmField
    val mockClientManagerRule = MockClientManagerRule()

    @Rule
    @JvmField
    val wireMock = WireMockRule(WireMockConfiguration.wireMockConfig().dynamicPort())

    protected lateinit var mockClient: CodeWhispererClientAdaptor

    internal lateinit var s3endpoint: String

    internal lateinit var fakeCreateUploadUrlResponse: CreateUploadUrlResponse
    internal lateinit var fakeCreateCodeScanResponse: StartCodeAnalysisResponse
    internal lateinit var fakeCreateCodeScanResponseFailed: StartCodeAnalysisResponse
    internal lateinit var fakeCreateCodeScanResponsePending: StartCodeAnalysisResponse
    internal lateinit var fakeListCodeScanFindingsResponse: ListCodeAnalysisFindingsResponse
    internal lateinit var fakeListCodeScanFindingsResponseE2E: ListCodeAnalysisFindingsResponse
    internal lateinit var fakeListCodeScanFindingsOutOfBoundsIndexResponse: ListCodeAnalysisFindingsResponse
    internal lateinit var fakeGetCodeScanResponse: GetCodeAnalysisResponse
    internal lateinit var fakeGetCodeScanResponsePending: GetCodeAnalysisResponse
    internal lateinit var fakeGetCodeScanResponseFailed: GetCodeAnalysisResponse
    internal lateinit var fakeGetCodeFixJobResponse: GetCodeFixJobResponse
    internal lateinit var fakeStartCodeFixJobResponse: StartCodeFixJobResponse

    internal val metadata: DefaultAwsResponseMetadata = DefaultAwsResponseMetadata.create(
        mapOf(AwsHeader.AWS_REQUEST_ID to CodeWhispererTestUtil.testRequestId)
    )

    internal lateinit var scanManagerSpy: CodeWhispererCodeScanManager
    internal lateinit var zipUploadManagerSpy: CodeWhispererZipUploadManager
    internal lateinit var project: Project

    @Before
    open fun setup() {
        project = projectRule.project
        s3endpoint = "http://127.0.0.1:${wireMock.port()}"

        scanManagerSpy = spy(CodeWhispererCodeScanManager.getInstance(project))
        doNothing().whenever(scanManagerSpy).buildCodeScanUI()
        doNothing().whenever(scanManagerSpy).showCodeScanUI()

        zipUploadManagerSpy = spy(CodeWhispererZipUploadManager.getInstance(project))
        doNothing().whenever(zipUploadManagerSpy).uploadArtifactToS3(any(), any(), any(), any(), isNull(), any(), any())
        projectRule.project.replaceService(CodeWhispererZipUploadManager::class.java, zipUploadManagerSpy, disposableRule.disposable)

        mockClient = mock<CodeWhispererClientAdaptor>().also {
            project.replaceService(CodeWhispererClientAdaptor::class.java, it, disposableRule.disposable)
        }

        ApplicationManager.getApplication().replaceService(
            CodeWhispererExplorerActionManager::class.java,
            mock {
                on { checkActiveCodeWhispererConnectionType(any()) } doReturn CodeWhispererLoginType.Accountless
            },
            disposableRule.disposable
        )
    }

    private fun setupCodeScanFinding(
        filePath: Path,
        startLine: Int,
        endLine: Int,
        codeSnippets: List<Pair<Int, String>>,
    ): String {
        val codeSnippetJson = codeSnippets.joinToString(",\n") { (number, content) ->
            """
        {
            "number": $number,
            "content": "$content"
        }
            """.trimIndent()
        }

        return """
        {
            "filePath": "${filePath.systemIndependentPath}",
            "startLine": $startLine,
            "endLine": $endLine,
            "title": "test",
            "description": {
                "text": "global variable",
                "markdown": "### global variable"
            },
            "detectorId": "detectorId",
            "detectorName": "detectorName",
            "findingId": "findingId",
            "relatedVulnerabilities": [],
            "codeSnippet": [
                $codeSnippetJson
            ],
            "severity": "Low",
            "remediation": {
                "recommendation": {
                    "text": "recommendationText",
                    "url": "recommendationUrl"
                },
                "suggestedFixes": []
            }
        }
        """.trimIndent()
    }

    private fun setupCodeScanFindings(filePath: Path) = """
    [
        ${setupCodeScanFinding(
        filePath,
        1,
        2,
        listOf(
            1 to "import numpy as np",
            2 to "               import from module1 import helper"
        )
    )},
        ${setupCodeScanFinding(
        filePath,
        1,
        2,
        listOf(
            1 to "import numpy as np",
            2 to "               import from module1 import helper"
        )
    )}
    ]
    """

    private fun setupCodeScanFindingsE2E(filePath: Path) = """
    [
        ${setupCodeScanFinding(
        filePath,
        1,
        2,
        listOf(
            1 to "using Utils;",
            2 to "using Helpers.Helper;"
        )
    )}
    ]
    """

    private fun setupCodeScanFindingsOutOfBounds(filePath: Path) = """
    [
        ${setupCodeScanFinding(
        filePath,
        99999,
        99999,
        kotlin.collections.listOf(
            1 to "import numpy as np",
            2 to "               import from module1 import helper"
        )
    )}
    ]
    """

    protected fun setupResponse(filePath: Path) {
        fakeCreateUploadUrlResponse = CreateUploadUrlResponse.builder()
            .uploadId(UPLOAD_ID)
            .uploadUrl(s3endpoint)
            .responseMetadata(metadata)
            .build() as CreateUploadUrlResponse

        fakeGetCodeFixJobResponse = GetCodeFixJobResponse.builder()
            .jobStatus(CodeFixJobStatus.SUCCEEDED)
            .suggestedFix(
                software.amazon.awssdk.services.codewhispererruntime.model.SuggestedFix.builder()
                    .codeDiff("diff")
                    .description("description")
                    .references(
                        Reference.builder()
                            .url(s3endpoint)
                            .licenseName("license")
                            .repository("repo")
                            .recommendationContentSpan(
                                Span.builder()
                                    .start(6)
                                    .end(8)
                                    .build()
                            )
                            .build()
                    )
                    .build()
            )
            .responseMetadata(metadata)
            .build() as GetCodeFixJobResponse

        fakeStartCodeFixJobResponse = StartCodeFixJobResponse.builder()
            .jobId(JOB_ID)
            .status(CodeFixJobStatus.IN_PROGRESS)
            .responseMetadata(metadata)
            .build() as StartCodeFixJobResponse

        fakeCreateCodeScanResponse = StartCodeAnalysisResponse.builder()
            .status(CodeAnalysisStatus.COMPLETED)
            .jobId(JOB_ID)
            .responseMetadata(metadata)
            .build() as StartCodeAnalysisResponse

        fakeCreateCodeScanResponseFailed = StartCodeAnalysisResponse.builder()
            .status(CodeAnalysisStatus.FAILED)
            .jobId(JOB_ID)
            .responseMetadata(metadata)
            .build() as StartCodeAnalysisResponse

        fakeCreateCodeScanResponsePending = StartCodeAnalysisResponse.builder()
            .status(CodeAnalysisStatus.PENDING)
            .jobId(JOB_ID)
            .responseMetadata(metadata)
            .build() as StartCodeAnalysisResponse

        fakeListCodeScanFindingsResponse = ListCodeAnalysisFindingsResponse.builder()
            .codeAnalysisFindings(setupCodeScanFindings(filePath))
            .responseMetadata(metadata)
            .build() as ListCodeAnalysisFindingsResponse

        fakeListCodeScanFindingsResponseE2E = ListCodeAnalysisFindingsResponse.builder()
            .codeAnalysisFindings(setupCodeScanFindingsE2E(filePath))
            .responseMetadata(metadata)
            .build() as ListCodeAnalysisFindingsResponse

        fakeListCodeScanFindingsOutOfBoundsIndexResponse = ListCodeAnalysisFindingsResponse.builder()
            .codeAnalysisFindings(setupCodeScanFindingsOutOfBounds(filePath))
            .responseMetadata(metadata)
            .build() as ListCodeAnalysisFindingsResponse

        fakeGetCodeScanResponse = GetCodeAnalysisResponse.builder()
            .status(CodeAnalysisStatus.COMPLETED)
            .responseMetadata(metadata)
            .build() as GetCodeAnalysisResponse

        fakeGetCodeScanResponsePending = GetCodeAnalysisResponse.builder()
            .status(CodeAnalysisStatus.PENDING)
            .responseMetadata(metadata)
            .build() as GetCodeAnalysisResponse

        fakeGetCodeScanResponseFailed = GetCodeAnalysisResponse.builder()
            .status(CodeAnalysisStatus.FAILED)
            .responseMetadata(metadata)
            .build() as GetCodeAnalysisResponse
    }

    protected fun getFakeRecommendationsOnNonExistentFile() = """
        [
            {
                "filePath": "non-exist.py",
                "startLine": 1,
                "endLine": 2,
                "title": "test",
                "description": {
                    "text": "global variable",
                    "markdown": "### global variable"
                },
                "detectorId": "detectorId",
                "detectorName": "detectorName",
                "findingId": "findingId",
                "relatedVulnerabilities": [],
                "codeSnippet": [
                    {
                        "number": 1,
                        "content": "codeBlock1"
                    },
                    {
                        "number": 2,
                        "content": "codeBlock2"
                    }
                ],
                "severity": "Low",
                "remediation": {
                    "recommendation": {
                        "text": "recommendationText",
                        "url": "recommendationUrl"
                    },
                    "suggestedFixes": []
                }
            }
        ]                
    """

    internal fun selectedFileLargerThanPayloadSizeThrowsException(sessionConfigSpy: CodeScanSessionConfig) {
        sessionConfigSpy.stub {
            onGeneric { getPayloadLimitInBytes() }.thenReturn(100)
        }
        assertThrows<CodeWhispererCodeScanException> {
            sessionConfigSpy.createPayload()
        }
    }

    internal fun getProjectPayloadMetadata(
        sessionConfigSpy: CodeScanSessionConfig,
        includedSourceFilesSize: Long,
        totalSize: Long,
        expectedTotalLines: Long,
        payloadLanguage: CodewhispererLanguage,
    ) {
        val payloadMetadata = sessionConfigSpy.getProjectPayloadMetadata()
        assertNotNull(payloadMetadata)
        val includedSourceFiles = payloadMetadata.sourceFiles
        val srcPayloadSize = payloadMetadata.payloadSize
        val totalLines = payloadMetadata.linesScanned
        val maxCountLanguage = payloadMetadata.language
        assertThat(includedSourceFiles.size).isEqualTo(includedSourceFilesSize)
        assertThat(srcPayloadSize).isEqualTo(totalSize)
        assertThat(totalLines).isEqualTo(expectedTotalLines)
        assertThat(maxCountLanguage).isEqualTo(payloadLanguage)
    }

    internal suspend fun assertE2ERunsSuccessfully(
        sessionConfigSpy: CodeScanSessionConfig,
        project: Project,
        expectedTotalLines: Long,
        expectedTotalFiles: Int,
        expectedTotalSize: Long,
        expectedTotalIssues: Int,
    ) {
        val codeScanContext = CodeScanSessionContext(project, sessionConfigSpy, CodeWhispererConstants.CodeAnalysisScope.PROJECT)
        val sessionMock = spy(CodeWhispererCodeScanSession(codeScanContext))

        ToolWindowManager.getInstance(project).registerToolWindow(
            RegisterToolWindowTask(
                id = ProblemsView.ID
            )
        )

        val codeScanResponse = sessionMock.run()
        assertInstanceOf<CodeScanResponse.Success>(codeScanResponse)
        assertThat(codeScanResponse.issues).hasSize(expectedTotalIssues)
        assertThat(codeScanResponse.responseContext.codeScanJobId).isEqualTo("jobId")

        val payloadContext = codeScanResponse.responseContext.payloadContext
        assertThat(payloadContext.totalLines).isEqualTo(expectedTotalLines)
        assertThat(payloadContext.totalFiles).isEqualTo(expectedTotalFiles)
        assertThat(payloadContext.srcPayloadSize).isEqualTo(expectedTotalSize)

        scanManagerSpy.testRenderResponseOnUIThread(
            codeScanResponse.issues,
            codeScanResponse.responseContext.payloadContext.scannedFiles,
        )
        assertNotNull(scanManagerSpy.getScanTree().model)
        val treeModel = scanManagerSpy.getScanTree().model as? CodeWhispererCodeScanTreeModel
        assertNotNull(treeModel)
        assertThat(treeModel.getTotalIssuesCount()).isEqualTo(expectedTotalIssues)
    }

    fun createCodeScanIssue(project: Project, virtualFile: VirtualFile): CodeWhispererCodeScanIssue =
        CodeWhispererCodeScanIssue(
            project = project,
            file = virtualFile,
            startLine = 10,
            startCol = 5,
            endLine = 15,
            endCol = 20,
            title = "Potential Security Vulnerability",
            description = Description(
                text = "A detailed description of the security issue found in the code",
                markdown = "# Security Issue\n\nA detailed description of the security issue found in the code"
            ),
            detectorId = "AWS-DETECTOR-001",
            detectorName = "SecurityScanner",
            findingId = "FINDING-123",
            ruleId = "RULE-456",
            relatedVulnerabilities = listOf(
                "CVE-2023-12345",
                "CVE-2023-67890"
            ),
            severity = "HIGH",
            recommendation = Recommendation(
                text = "Consider implementing secure coding practices",
                url = "https://docs.aws.amazon.com/security-best-practices"
            ),
            suggestedFixes = emptyList(), // Empty list as requested
            codeSnippet = listOf(
                CodeLine(
                    number = 10,
                    content = "val unsecureCode = performOperation()"
                ),
                CodeLine(
                    number = 11,
                    content = "processData(unsecureCode)"
                )
            ),
            scanJobId = "scanJobId"
        )

// You might need these data classes depending on your implementation
    data class Description(val message: String)
    data class Recommendation(val text: String)
    data class SuggestedFix(val description: String, val code: String)
    data class CodeLine(val lineNumber: Int, val content: String)

    companion object {
        const val UPLOAD_ID = "uploadId"
        const val JOB_ID = "jobId"
        const val KMS_KEY_ARN = "kmsKeyArn"
        val REQUEST_HEADERS = mapOf(
            "Content-Type" to "application/zip",
            "test" to "aws:test",
        )
    }
}
