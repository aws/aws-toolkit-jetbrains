// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonq.webview

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.intellij.testFramework.replaceService
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mockStatic
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.spy
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import software.amazon.q.jetbrains.utils.satisfiesKt
import software.aws.toolkits.jetbrains.services.amazonq.AmazonQTestBase
import software.aws.toolkits.jetbrains.services.codewhisperer.codescan.CodeWhispererCodeScanIssue
import software.aws.toolkits.jetbrains.services.codewhisperer.codescan.CodeWhispererCodeScanManager
import software.aws.toolkits.jetbrains.services.codewhisperer.codescan.Description
import software.aws.toolkits.jetbrains.services.codewhisperer.codescan.Recommendation
import software.aws.toolkits.jetbrains.services.codewhisperer.codescan.SuggestedFix
import software.aws.toolkits.jetbrains.services.codewhisperer.util.CodeWhispererConstants

class BrowserConnectorTest : AmazonQTestBase() {
    private lateinit var browserConnector: BrowserConnector
    private lateinit var mockCodeScanManager: CodeWhispererCodeScanManager
    private lateinit var mockLocalFileSystem: LocalFileSystem
    private lateinit var mockFileDocumentManager: FileDocumentManager
    private lateinit var fixture: CodeInsightTestFixture

    @Before
    override fun setup() {
        super.setup()
        fixture = projectRule.fixture

        mockCodeScanManager = mock()
        mockLocalFileSystem = mock()
        mockFileDocumentManager = mock()

        project.replaceService(CodeWhispererCodeScanManager::class.java, mockCodeScanManager, disposableRule.disposable)

        browserConnector = spy(BrowserConnector(project = project))
    }

    @Test
    fun `parseFindingsMessages should handle no additionalMessages`() {
        browserConnector.parseFindingsMessages("""""")

        verify(mockCodeScanManager, never()).addOnDemandIssues(any(), any(), any())
    }

    @Test
    fun `parseFindingsMessages should handle null additionalMessages`() {
        browserConnector.parseFindingsMessages(
            """
            {
              "additionalMessages": null
            }
            """.trimIndent()
        )

        verify(mockCodeScanManager, never()).addOnDemandIssues(any(), any(), any())
    }

    @Test
    fun `parseFindingsMessages should handle empty additionalMessages`() {
        browserConnector.parseFindingsMessages(
            """
            {
              "additionalMessages": []
            }
            """.trimIndent()
        )

        verify(mockCodeScanManager, never()).addOnDemandIssues(any(), any(), any())
    }

    @Test
    fun `parseFindingsMessages should filter messages with CODE_REVIEW_FINDINGS_SUFFIX`() {
        val findingsMessage = """
            {
                "additionalMessages": [
                    {
                        "messageId": "test_codeReviewFindings",
                        "body": "[{\"filePath\": \"/test/file.kt\", \"issues\": []}]"
                    },
                    {
                        "messageId": "other_message",
                        "body": "other content"
                    }
                ]
            }
        """.trimIndent()

        assertThat(browserConnector.deserializeFindings(findingsMessage))
            .singleElement()
            .satisfiesKt {
                assertThat(it.messageId).isEqualTo("test_codeReviewFindings")
                assertThat(it.body).singleElement()
                    .satisfiesKt { finding ->
                        assertThat(finding.filePath).isEqualTo("/test/file.kt")
                    }
            }
    }

    @Test
    fun `parseFindingsMessages should filter messages with DISPLAY_FINDINGS_SUFFIX`() {
        val findingsMessage = """
            {
                "additionalMessages": [
                    {
                        "messageId": "test_displayFindings",
                        "body": "[{\"filePath\": \"/test/file.kt\", \"issues\": []}]"
                    },
                    {
                        "messageId": "other_message",
                        "body": "other content"
                    }
                ]
            }
        """.trimIndent()

        assertThat(browserConnector.deserializeFindings(findingsMessage))
            .singleElement()
            .satisfiesKt {
                assertThat(it.messageId).isEqualTo("test_displayFindings")
                assertThat(it.body).singleElement()
                    .satisfiesKt { finding ->
                        assertThat(finding.filePath).isEqualTo("/test/file.kt")
                    }
            }
    }

    @Test
    fun `parseFindingsMessages should process valid findings and verify mappedFindings populated`() {
        val mockVirtualFile = mock<VirtualFile> {
            on { isDirectory } doReturn false
        }
        val mockDocument = mock<Document> {
            on { lineCount } doReturn 5
            on { getLineStartOffset(0) } doReturn 0
            on { getLineEndOffset(0) } doReturn 10
        }

        mockStatic(LocalFileSystem::class.java).use { localFileSystemMock ->
            localFileSystemMock.`when`<LocalFileSystem> { LocalFileSystem.getInstance() }.thenReturn(mockLocalFileSystem)
            whenever(mockLocalFileSystem.findFileByIoFile(any())) doReturn mockVirtualFile

            mockStatic(FileDocumentManager::class.java).use { fileDocumentManagerMock ->
                fileDocumentManagerMock.`when`<FileDocumentManager> { FileDocumentManager.getInstance() } doReturn mockFileDocumentManager
                whenever(mockFileDocumentManager.getDocument(mockVirtualFile)) doReturn mockDocument
                whenever(mockCodeScanManager.isIgnoredIssue(any(), any(), any(), any())) doReturn false

                val issue = FlareCodeScanIssue(
                    startLine = 1, endLine = 1, comment = "Test comment", title = "Test Issue",
                    description = Description("Test description", "Test text"), detectorId = "test-detector",
                    detectorName = "Test Detector", findingId = "test-finding-id", ruleId = "test-rule",
                    relatedVulnerabilities = listOf("CVE-2023-1234"), severity = "HIGH",
                    recommendation = Recommendation("Fix this", "https://example.com"),
                    suggestedFixes = listOf(SuggestedFix("Fix code", "Fixed code")),
                    scanJobId = "test-job-id", language = "kotlin", autoDetected = false,
                    filePath = "/test/file.kt", findingContext = "test context"
                )

                val aggregatedIssue = AggregatedCodeScanIssue("/test/file.kt", listOf(issue))
                val findingsMessage = """
                {
                    "additionalMessages": [
                        {
                            "messageId": "test_codeReviewFindings",
                            "body": "${jacksonObjectMapper().writeValueAsString(listOf(aggregatedIssue)).replace("\"", "\\\"")}"
                        },
                        {
                            "messageId": "other_message",
                            "body": "other content"
                        }
                    ]
                }
                """.trimIndent()

                browserConnector.parseFindingsMessages(findingsMessage)

                val issuesCaptor = argumentCaptor<List<CodeWhispererCodeScanIssue>>()
                verify(mockCodeScanManager).addOnDemandIssues(
                    issuesCaptor.capture(),
                    any(),
                    eq(CodeWhispererConstants.CodeAnalysisScope.AGENTIC)
                )

                assertThat(issuesCaptor.firstValue.isNotEmpty())
                assertThat(issuesCaptor.firstValue[0].title == "Test Issue")
            }
        }
    }

    @Test
    fun `parseFindingsMessages should skip directory files and not populate mappedFindings`() {
        val mockDirectoryFile = mock<VirtualFile> { on { isDirectory } doReturn true }

        mockStatic(LocalFileSystem::class.java).use { localFileSystemMock ->
            localFileSystemMock.`when`<LocalFileSystem> { LocalFileSystem.getInstance() } doReturn mockLocalFileSystem
            whenever(mockLocalFileSystem.findFileByIoFile(any())) doReturn mockDirectoryFile

            val issue = FlareCodeScanIssue(
                startLine = 1, endLine = 1, comment = null, title = "Test Issue",
                description = Description("Test description", "Test text"), detectorId = "test-detector",
                detectorName = "Test Detector", findingId = "test-finding-id", ruleId = null,
                relatedVulnerabilities = emptyList(), severity = "MEDIUM",
                recommendation = Recommendation("Fix this", "https://example.com"), suggestedFixes = emptyList(),
                scanJobId = "test-job-id", language = "kotlin", autoDetected = true,
                filePath = "/test/directory", findingContext = "test context"
            )

            val aggregatedIssue = AggregatedCodeScanIssue("/test/directory", listOf(issue))
            val findingsMessage = """
            {
                "additionalMessages": [
                    {
                        "messageId": "test_displayFindings",
                        "body": "${jacksonObjectMapper().writeValueAsString(listOf(aggregatedIssue)).replace("\"", "\\\"")}"
                    },
                    {
                        "messageId": "other_message",
                        "body": "other content"
                    }
                ]
            }
            """.trimIndent()

            browserConnector.parseFindingsMessages(findingsMessage)

            verify(mockCodeScanManager, never()).addOnDemandIssues(
                any(),
                any(),
                eq(CodeWhispererConstants.CodeAnalysisScope.AGENTIC)
            )
        }
    }

    @Test
    fun `parseFindingsMessages should handle invalid JSON gracefully`() {
        val findingsMessage = """
            {
                "additionalMessages": [
                    {
                        "messageId": "test_codeReviewFindings",
                        "body": "invalid json"
                    },
                    {
                        "body": "invalid json"
                    }
                ]
            }
        """.trimIndent()

        browserConnector.parseFindingsMessages(findingsMessage)

        verify(mockCodeScanManager, never()).addOnDemandIssues(any(), any(), any())
    }
}
