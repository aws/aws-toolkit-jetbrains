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
    fun `parseFindingsMessages should handle empty additionalMessages`() {
        val messagesMap = mapOf<String, Any>()

        browserConnector.parseFindingsMessages(messagesMap)

        verify(mockCodeScanManager, never()).addOnDemandIssues(any(), any(), any())
    }

    @Test
    fun `parseFindingsMessages should handle null additionalMessages`() {
        val messagesMap = mapOf("additionalMessages" to null)

        browserConnector.parseFindingsMessages(messagesMap)

        verify(mockCodeScanManager, never()).addOnDemandIssues(any(), any(), any())
    }

    @Test
    fun `parseFindingsMessages should filter messages with CODE_REVIEW_FINDINGS_SUFFIX`() {
        val findingsMessage = mapOf(
            "messageId" to "test_codeReviewFindings",
            "body" to """[{"filePath": "/test/file.kt", "issues": []}]"""
        )
        val otherMessage = mapOf(
            "messageId" to "other_message",
            "body" to "other content"
        )
        val additionalMessages = mutableListOf<Map<String, Any>>(findingsMessage, otherMessage)
        val messagesMap = mapOf("additionalMessages" to additionalMessages)

        browserConnector.parseFindingsMessages(messagesMap)

        assert(additionalMessages.size == 1)
        assert(additionalMessages[0] == otherMessage)
    }

    @Test
    fun `parseFindingsMessages should filter messages with DISPLAY_FINDINGS_SUFFIX`() {
        val findingsMessage = mapOf(
            "messageId" to "test_displayFindings",
            "body" to """[{"filePath": "/test/file.kt", "issues": []}]"""
        )
        val additionalMessages = mutableListOf<Map<String, Any>>(findingsMessage)
        val messagesMap = mapOf("additionalMessages" to additionalMessages)

        browserConnector.parseFindingsMessages(messagesMap)

        assert(additionalMessages.isEmpty())
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

                val issue = BrowserConnector.FlareCodeScanIssue(
                    startLine = 1, endLine = 1, comment = "Test comment", title = "Test Issue",
                    description = Description("Test description", "Test text"), detectorId = "test-detector",
                    detectorName = "Test Detector", findingId = "test-finding-id", ruleId = "test-rule",
                    relatedVulnerabilities = listOf("CVE-2023-1234"), severity = "HIGH",
                    recommendation = Recommendation("Fix this", "https://example.com"),
                    suggestedFixes = listOf(SuggestedFix("Fix code", "Fixed code")),
                    scanJobId = "test-job-id", language = "kotlin", autoDetected = false,
                    filePath = "/test/file.kt", findingContext = "test context"
                )

                val aggregatedIssue = BrowserConnector.AggregatedCodeScanIssue("/test/file.kt", listOf(issue))
                val findingsMessage = mapOf(
                    "messageId" to "test_codeReviewFindings",
                    "body" to jacksonObjectMapper().writeValueAsString(listOf(aggregatedIssue))
                )
                val additionalMessages = mutableListOf<Map<String, Any>>(findingsMessage)
                val messagesMap = mapOf("additionalMessages" to additionalMessages)

                browserConnector.parseFindingsMessages(messagesMap)

                val issuesCaptor = argumentCaptor<List<CodeWhispererCodeScanIssue>>()
                verify(mockCodeScanManager).addOnDemandIssues(
                    issuesCaptor.capture(),
                    any(),
                    eq(CodeWhispererConstants.CodeAnalysisScope.AGENTIC)
                )

                assert(additionalMessages.isEmpty())
                assert(issuesCaptor.firstValue.isNotEmpty())
                assert(issuesCaptor.firstValue[0].title == "Test Issue")
            }
        }
    }

    @Test
    fun `parseFindingsMessages should skip directory files and not populate mappedFindings`() {
        val mockDirectoryFile = mock<VirtualFile> { on { isDirectory } doReturn true }

        mockStatic(LocalFileSystem::class.java).use { localFileSystemMock ->
            localFileSystemMock.`when`<LocalFileSystem> { LocalFileSystem.getInstance() } doReturn mockLocalFileSystem
            whenever(mockLocalFileSystem.findFileByIoFile(any())) doReturn mockDirectoryFile

            val issue = BrowserConnector.FlareCodeScanIssue(
                startLine = 1, endLine = 1, comment = null, title = "Test Issue",
                description = Description("Test description", "Test text"), detectorId = "test-detector",
                detectorName = "Test Detector", findingId = "test-finding-id", ruleId = null,
                relatedVulnerabilities = emptyList(), severity = "MEDIUM",
                recommendation = Recommendation("Fix this", "https://example.com"), suggestedFixes = emptyList(),
                scanJobId = "test-job-id", language = "kotlin", autoDetected = true,
                filePath = "/test/directory", findingContext = "test context"
            )

            val aggregatedIssue = BrowserConnector.AggregatedCodeScanIssue("/test/directory", listOf(issue))
            val findingsMessage = mapOf(
                "messageId" to "test_displayFindings",
                "body" to jacksonObjectMapper().writeValueAsString(listOf(aggregatedIssue))
            )
            val additionalMessages = mutableListOf<Map<String, Any>>(findingsMessage)
            val messagesMap = mapOf("additionalMessages" to additionalMessages)

            browserConnector.parseFindingsMessages(messagesMap)

            val issuesCaptor = argumentCaptor<List<CodeWhispererCodeScanIssue>>()
            verify(mockCodeScanManager).addOnDemandIssues(
                issuesCaptor.capture(),
                any(),
                eq(CodeWhispererConstants.CodeAnalysisScope.AGENTIC)
            )

            assert(issuesCaptor.firstValue.isEmpty())
            assert(additionalMessages.isEmpty())
        }
    }

    @Test
    fun `parseFindingsMessages should handle invalid JSON gracefully`() {
        val findingsMessage = mapOf(
            "messageId" to "test_codeReviewFindings",
            "body" to "invalid json"
        )
        val additionalMessages = mutableListOf<Map<String, Any>>(findingsMessage)
        val messagesMap = mapOf("additionalMessages" to additionalMessages)

        browserConnector.parseFindingsMessages(messagesMap)

        assert(additionalMessages.isEmpty())
    }
}
