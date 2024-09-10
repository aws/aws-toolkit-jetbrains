// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.codewhisperer.codescan

import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import com.github.tomakehurst.wiremock.junit.WireMockRule
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.replaceService
import com.intellij.util.io.systemIndependentPath
import org.assertj.core.api.Assertions
import org.junit.Before
import org.junit.Rule
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.doNothing
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.spy
import org.mockito.kotlin.stub
import org.mockito.kotlin.whenever
import software.amazon.awssdk.awscore.DefaultAwsResponseMetadata
import software.amazon.awssdk.awscore.util.AwsHeader
import software.amazon.awssdk.services.codewhisperer.model.CodeScanStatus
import software.amazon.awssdk.services.codewhisperer.model.CreateCodeScanResponse
import software.amazon.awssdk.services.codewhisperer.model.GetCodeScanResponse
import software.amazon.awssdk.services.codewhisperer.model.ListCodeScanFindingsResponse
import software.amazon.awssdk.services.codewhispererruntime.model.CreateUploadUrlResponse
import software.aws.toolkits.jetbrains.core.MockClientManagerRule
import software.aws.toolkits.jetbrains.services.codewhisperer.CodeWhispererTestUtil
import software.aws.toolkits.jetbrains.services.codewhisperer.codescan.sessionconfig.CodeScanSessionConfig
import software.aws.toolkits.jetbrains.services.codewhisperer.credentials.CodeWhispererClientAdaptor
import software.aws.toolkits.jetbrains.services.codewhisperer.credentials.CodeWhispererLoginType
import software.aws.toolkits.jetbrains.services.codewhisperer.explorer.CodeWhispererExplorerActionManager
import software.aws.toolkits.jetbrains.utils.rules.CodeInsightTestFixtureRule
import software.aws.toolkits.telemetry.CodewhispererLanguage
import java.nio.file.Path
import kotlin.test.assertNotNull

open class CodeWhispererCodeScanTestBase(@Rule @JvmField val projectRule: CodeInsightTestFixtureRule) {
    @Rule
    @JvmField
    val applicationRule = ApplicationRule()

    @Rule
    @JvmField
    val disposableRule = DisposableRule()

    @Rule
    @JvmField
    val mockClientManagerRule = MockClientManagerRule()

    @Rule
    @JvmField
    val wireMock = WireMockRule(WireMockConfiguration.wireMockConfig().dynamicPort())

    protected val project
        get() = projectRule.project

    protected lateinit var mockClient: CodeWhispererClientAdaptor
    protected lateinit var s3endpoint: String
    protected lateinit var fakeCreateUploadUrlResponse: CreateUploadUrlResponse
    protected lateinit var fakeCreateCodeScanResponse: CreateCodeScanResponse
    protected lateinit var fakeCreateCodeScanResponseFailed: CreateCodeScanResponse
    protected lateinit var fakeCreateCodeScanResponsePending: CreateCodeScanResponse
    protected lateinit var fakeListCodeScanFindingsResponse: ListCodeScanFindingsResponse
    protected lateinit var fakeListCodeScanFindingsResponseE2E: ListCodeScanFindingsResponse
    protected lateinit var fakeListCodeScanFindingsOutOfBoundsIndexResponse: ListCodeScanFindingsResponse
    protected lateinit var fakeGetCodeScanResponse: GetCodeScanResponse
    protected lateinit var fakeGetCodeScanResponsePending: GetCodeScanResponse
    protected lateinit var fakeGetCodeScanResponseFailed: GetCodeScanResponse

    internal val metadata: DefaultAwsResponseMetadata = DefaultAwsResponseMetadata.create(
        mapOf(AwsHeader.AWS_REQUEST_ID to CodeWhispererTestUtil.testRequestId)
    )

    internal lateinit var scanManagerSpy: CodeWhispererCodeScanManager

    @Before
    open fun setup() {
        s3endpoint = "http://127.0.0.1:${wireMock.port()}"

        scanManagerSpy = spy(CodeWhispererCodeScanManager(project))
        doNothing().whenever(scanManagerSpy).addCodeScanUI(any())
        projectRule.project.replaceService(CodeWhispererCodeScanManager::class.java, scanManagerSpy, disposableRule.disposable)

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
        codeSnippets: List<Pair<Int, String>>
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
            "severity": "severity",
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
        listOf(
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

        fakeCreateCodeScanResponse = CreateCodeScanResponse.builder()
            .status(CodeScanStatus.COMPLETED)
            .jobId(JOB_ID)
            .responseMetadata(metadata)
            .build() as CreateCodeScanResponse

        fakeCreateCodeScanResponseFailed = CreateCodeScanResponse.builder()
            .status(CodeScanStatus.FAILED)
            .jobId(JOB_ID)
            .responseMetadata(metadata)
            .build() as CreateCodeScanResponse

        fakeCreateCodeScanResponsePending = CreateCodeScanResponse.builder()
            .status(CodeScanStatus.PENDING)
            .jobId(JOB_ID)
            .responseMetadata(metadata)
            .build() as CreateCodeScanResponse

        fakeListCodeScanFindingsResponse = ListCodeScanFindingsResponse.builder()
            .codeScanFindings(setupCodeScanFindings(filePath))
            .responseMetadata(metadata)
            .build() as ListCodeScanFindingsResponse

        fakeListCodeScanFindingsResponseE2E = ListCodeScanFindingsResponse.builder()
            .codeScanFindings(setupCodeScanFindingsE2E(filePath))
            .responseMetadata(metadata)
            .build() as ListCodeScanFindingsResponse

        fakeListCodeScanFindingsOutOfBoundsIndexResponse = ListCodeScanFindingsResponse.builder()
            .codeScanFindings(setupCodeScanFindingsOutOfBounds(filePath))
            .responseMetadata(metadata)
            .build() as ListCodeScanFindingsResponse

        fakeGetCodeScanResponse = GetCodeScanResponse.builder()
            .status(CodeScanStatus.COMPLETED)
            .responseMetadata(metadata)
            .build() as GetCodeScanResponse

        fakeGetCodeScanResponsePending = GetCodeScanResponse.builder()
            .status(CodeScanStatus.PENDING)
            .responseMetadata(metadata)
            .build() as GetCodeScanResponse

        fakeGetCodeScanResponseFailed = GetCodeScanResponse.builder()
            .status(CodeScanStatus.FAILED)
            .responseMetadata(metadata)
            .build() as GetCodeScanResponse
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
                "severity": "severity",
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
        payloadLanguage: CodewhispererLanguage
    ) {
        val payloadMetadata = sessionConfigSpy.getProjectPayloadMetadata()
        assertNotNull(payloadMetadata)
        val includedSourceFiles = payloadMetadata.sourceFiles
        val srcPayloadSize = payloadMetadata.payloadSize
        val totalLines = payloadMetadata.linesScanned
        val maxCountLanguage = payloadMetadata.language
        Assertions.assertThat(includedSourceFiles.size).isEqualTo(includedSourceFilesSize)
        Assertions.assertThat(srcPayloadSize).isEqualTo(totalSize)
        Assertions.assertThat(totalLines).isEqualTo(expectedTotalLines)
        Assertions.assertThat(maxCountLanguage).isEqualTo(payloadLanguage)
    }

    internal suspend fun assertE2ERunsSuccessfully(
        sessionConfigSpy: CodeScanSessionConfig,
        project: Project,
        expectedTotalLines: Long,
        expectedTotalFiles: Int,
        expectedTotalSize: Long,
        expectedTotalIssues: Int
    ) {
        val codeScanContext = CodeScanSessionContext(
            project,
            sessionConfigSpy,
            software.aws.toolkits.jetbrains.services.codewhisperer.util.CodeWhispererConstants.CodeAnalysisScope.PROJECT
        )
        val sessionMock = spy(CodeWhispererCodeScanSession(codeScanContext))
        doNothing().`when`(sessionMock).uploadArtifactToS3(
            any(),
            any(),
            any(),
            any(),
            org.mockito.kotlin.isNull(),
            any()
        )

        com.intellij.openapi.wm.ToolWindowManager.Companion.getInstance(project).registerToolWindow(
            com.intellij.openapi.wm.RegisterToolWindowTask(
                id = com.intellij.analysis.problemsView.toolWindow.ProblemsView.ID
            )
        )

        val codeScanResponse = sessionMock.run()
        org.junit.jupiter.api.assertInstanceOf<CodeScanResponse.Success>(codeScanResponse)
        Assertions.assertThat(codeScanResponse.issues).hasSize(expectedTotalIssues)
        Assertions.assertThat(codeScanResponse.responseContext.codeScanJobId).isEqualTo("jobId")

        val payloadContext = codeScanResponse.responseContext.payloadContext
        Assertions.assertThat(payloadContext.totalLines).isEqualTo(expectedTotalLines)
        Assertions.assertThat(payloadContext.totalFiles).isEqualTo(expectedTotalFiles)
        Assertions.assertThat(payloadContext.srcPayloadSize).isEqualTo(expectedTotalSize)

        scanManagerSpy.testRenderResponseOnUIThread(
            codeScanResponse.issues,
            codeScanResponse.responseContext.payloadContext.scannedFiles,
        )
        assertNotNull(scanManagerSpy.getScanTree().model)
        val treeModel = scanManagerSpy.getScanTree().model as? CodeWhispererCodeScanTreeModel
        assertNotNull(treeModel)
        Assertions.assertThat(treeModel.getTotalIssuesCount()).isEqualTo(expectedTotalIssues)
    }

    companion object {
        const val UPLOAD_ID = "uploadId"
        const val JOB_ID = "jobId"
    }
}
