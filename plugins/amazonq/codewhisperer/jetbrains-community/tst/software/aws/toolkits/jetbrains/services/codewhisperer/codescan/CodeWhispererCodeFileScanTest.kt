// Copyright 2022 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.codewhisperer.codescan

import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.psi.PsiFile
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.apache.commons.codec.digest.DigestUtils
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.mock
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doNothing
import org.mockito.kotlin.eq
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.isNull
import org.mockito.kotlin.spy
import org.mockito.kotlin.stub
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import software.amazon.awssdk.awscore.exception.AwsErrorDetails
import software.amazon.awssdk.services.codewhisperer.model.CodeWhispererException
import software.amazon.awssdk.services.codewhispererruntime.model.CreateUploadUrlRequest
import software.aws.toolkits.core.utils.WaiterTimeoutException
import software.aws.toolkits.jetbrains.services.codewhisperer.codescan.sessionconfig.CodeScanSessionConfig
import software.aws.toolkits.jetbrains.services.codewhisperer.codescan.sessionconfig.Payload
import software.aws.toolkits.jetbrains.services.codewhisperer.codescan.sessionconfig.PayloadContext
import software.aws.toolkits.jetbrains.services.codewhisperer.util.CodeWhispererConstants
import software.aws.toolkits.jetbrains.services.codewhisperer.util.CodeWhispererConstants.TOTAL_MILLIS_IN_SECOND
import software.aws.toolkits.jetbrains.utils.isInstanceOf
import software.aws.toolkits.jetbrains.utils.isInstanceOfSatisfying
import software.aws.toolkits.jetbrains.utils.rules.PythonCodeInsightTestFixtureRule
import software.aws.toolkits.telemetry.CodewhispererLanguage
import java.io.FileInputStream
import java.lang.management.ManagementFactory
import java.util.Base64
import java.util.UUID
import java.util.zip.ZipFile
import kotlin.io.path.relativeTo
import kotlin.test.assertNotNull

class CodeWhispererCodeFileScanTest : CodeWhispererCodeScanTestBase(PythonCodeInsightTestFixtureRule()) {
    private val codeScanName = UUID.randomUUID().toString()
    private val payloadContext = PayloadContext(CodewhispererLanguage.Python, 1, 1, 10, listOf(), 600, 200)

    private lateinit var pyPsiFile: PsiFile
    private lateinit var ktPsiFile: PsiFile
    private lateinit var pySession: CodeScanSessionConfig
    private lateinit var codeScanSessionSpy: CodeWhispererCodeScanSession

    @Before
    override fun setup() {
        super.setup()

        pyPsiFile = projectRule.fixture.addFileToProject(
            "/test.py",
            """import numpy as np
               import from module1 import helper
               
               def add(a, b):
                  return a + b
                  
            """.trimMargin()
        )

        ktPsiFile = projectRule.fixture.addFileToProject(
            "/test.kt",
            // write simple addition function in kotlin
            """
                fun main() {
                    val a = 1
                    val b = 2
                    val c = a + b
                    println(c)
                }
            """.trimMargin()
        )

        projectRule.fixture.addFileToProject(
            "/subtract.java",
            """public class MathOperations {
                public static int subtract(int a, int b) {
                    return a - b; 
                    }
                public static void main(String[] args) {    
                    int num1 = 10;
                    int num2 = 5;
                    int result = subtract(num1, num2);
                    System.out.println(result);
                    }
                }     
            """.trimMargin()
        )

        pySession = spy(
            CodeScanSessionConfig.create(
                pyPsiFile.virtualFile,
                project,
                CodeWhispererConstants.CodeAnalysisScope.FILE
            )
        )
        setupResponse(pyPsiFile.virtualFile.toNioPath().relativeTo(pySession.projectRoot.toNioPath()))

        pySession.stub {
            onGeneric { pySession.createPayload() }.thenReturn(Payload(payloadContext, pyPsiFile.virtualFile.toNioPath().toFile()))
        }

        // Mock CodeWhispererClient needs to be setup before initializing CodeWhispererCodeScanSession
        val pySessionContext = CodeScanSessionContext(project, pySession, CodeWhispererConstants.CodeAnalysisScope.FILE)
        codeScanSessionSpy = spy(CodeWhispererCodeScanSession(pySessionContext))
        doNothing().whenever(codeScanSessionSpy).uploadArtifactToS3(any(), any(), any(), any(), isNull(), any())

        mockClient.stub {
            // setupResponse dynamically modifies these fake responses so this is very hard to follow and makes me question if we even need this
            onGeneric { createUploadUrl(any()) }.thenAnswer { fakeCreateUploadUrlResponse }
            onGeneric { createCodeScan(any(), any()) }.thenAnswer { fakeCreateCodeScanResponse }
            onGeneric { getCodeScan(any(), any()) }.thenAnswer { fakeGetCodeScanResponse }
            onGeneric { listCodeScanFindings(any(), any()) }.thenAnswer { fakeListCodeScanFindingsResponse }
        }
    }

    @Test
    fun `test run() - measure CPU and memory usage with payload of 200KB`() {
        // Create a 200KB file
        val content = "a".repeat(200 * 1024)
        val psiFile = projectRule.fixture.addFileToProject("test.txt", content)

        val sessionConfig = spy(
            CodeScanSessionConfig.create(
                psiFile.virtualFile,
                project,
                CodeWhispererConstants.CodeAnalysisScope.FILE
            )
        )
        setupResponse(psiFile.virtualFile.toNioPath().relativeTo(sessionConfig.projectRoot.toNioPath()))
        val sessionContext = CodeScanSessionContext(project, sessionConfig, CodeWhispererConstants.CodeAnalysisScope.FILE)
        val session = spy(CodeWhispererCodeScanSession(sessionContext))
        doNothing().whenever(session).uploadArtifactToS3(any(), any(), any(), any(), isNull(), any())

        // Set up CPU and Memory monitoring
        val runtime = Runtime.getRuntime()
        val bean = ManagementFactory.getThreadMXBean()
        val startCpuTime = bean.getCurrentThreadCpuTime()
        val startMemoryUsage = runtime.totalMemory() - runtime.freeMemory()
        val startSystemTime = System.nanoTime()

        // Run the code scan
        runBlocking {
            session.run()
        }

        // Calculate CPU and memory usage
        val endCpuTime = bean.getCurrentThreadCpuTime()
        val endMemoryUsage = runtime.totalMemory() - runtime.freeMemory()
        val endSystemTime = System.nanoTime()

        val cpuTimeUsedNanos = endCpuTime - startCpuTime
        val cpuTimeUsedSeconds = cpuTimeUsedNanos / 1_000_000_000.0
        val elapsedTimeSeconds = (endSystemTime - startSystemTime) / 1_000_000_000.0

        val memoryUsed = endMemoryUsage - startMemoryUsage
        val memoryUsedInMB = memoryUsed / (1024.0 * 1024.0) // Converting into MB

        // Calculate CPU usage in percentage
        val cpuUsagePercentage = (cpuTimeUsedSeconds / elapsedTimeSeconds) * 100

        assertThat(cpuTimeUsedSeconds).isLessThan(5.0)
        assertThat(cpuUsagePercentage).isLessThan(30.0)
        assertThat(memoryUsedInMB).isLessThan(200.0) // Memory used should be less than 200MB
    }

    @Test
    fun `test run() - measure CPU and memory usage with payload of 150KB`() {
        // Create a 150KB file
        val codeContentForPayload = "a".repeat(150 * 1024)
        val psiFile = projectRule.fixture.addFileToProject("test.txt", codeContentForPayload)

        val sessionConfig = spy(
            CodeScanSessionConfig.create(
                psiFile.virtualFile,
                project,
                CodeWhispererConstants.CodeAnalysisScope.FILE
            )
        )
        setupResponse(psiFile.virtualFile.toNioPath().relativeTo(sessionConfig.projectRoot.toNioPath()))
        val sessionContext = CodeScanSessionContext(project, sessionConfig, CodeWhispererConstants.CodeAnalysisScope.FILE)
        val session = spy(CodeWhispererCodeScanSession(sessionContext))
        doNothing().whenever(session).uploadArtifactToS3(any(), any(), any(), any(), isNull(), any())

        // Set up CPU and Memory monitoring
        val runtime = Runtime.getRuntime()
        val bean = ManagementFactory.getThreadMXBean()
        val startCpuTime = bean.getCurrentThreadCpuTime()
        val startMemoryUsage = runtime.totalMemory() - runtime.freeMemory()
        val startSystemTime = System.nanoTime()

        // Run the code scan
        runBlocking {
            session.run()
        }

        // Calculate CPU and memory usage
        val endCpuTime = bean.getCurrentThreadCpuTime()
        val endMemoryUsage = runtime.totalMemory() - runtime.freeMemory()
        val endSystemTime = System.nanoTime()

        val cpuTimeUsedNanos = endCpuTime - startCpuTime
        val cpuTimeUsedSeconds = cpuTimeUsedNanos / 1_000_000_000.0
        val elapsedTimeSeconds = (endSystemTime - startSystemTime) / 1_000_000_000.0

        val memoryUsed = endMemoryUsage - startMemoryUsage
        val memoryUsedInMB = memoryUsed / (1024.0 * 1024.0) // Converting into MB

        // Calculate CPU usage in percentage
        val cpuUsagePercentage = (cpuTimeUsedSeconds / elapsedTimeSeconds) * 100

        assertThat(cpuTimeUsedSeconds).isLessThan(5.0)
        assertThat(cpuUsagePercentage).isLessThan(30.0)
        assertThat(memoryUsedInMB).isLessThan(200.0) // Memory used should be less than 200MB
    }

    @Test
    fun `test createUploadUrlAndUpload()`() {
        val file = pyPsiFile.virtualFile.toNioPath().toFile()
        val fileMd5: String = Base64.getEncoder().encodeToString(DigestUtils.md5(FileInputStream(file)))
        codeScanSessionSpy.stub {
            onGeneric { codeScanSessionSpy.createUploadUrl(any(), any(), any()) }
                .thenReturn(fakeCreateUploadUrlResponse)
        }

        codeScanSessionSpy.createUploadUrlAndUpload(file, "artifactType", codeScanName)

        val inOrder = inOrder(codeScanSessionSpy)
        inOrder.verify(codeScanSessionSpy).createUploadUrl(eq(fileMd5), eq("artifactType"), any())
        inOrder.verify(codeScanSessionSpy).uploadArtifactToS3(
            eq(fakeCreateUploadUrlResponse.uploadUrl()),
            eq(fakeCreateUploadUrlResponse.uploadId()),
            eq(file),
            eq(fileMd5),
            eq(null),
            any()
        )
    }

    @Test
    fun `test createUploadUrl()`() {
        val response = codeScanSessionSpy.createUploadUrl("md5", "type", codeScanName)

        argumentCaptor<CreateUploadUrlRequest>().apply {
            verify(mockClient).createUploadUrl(capture())
            assertThat(response.uploadUrl()).isEqualTo(s3endpoint)
            assertThat(response.uploadId()).isEqualTo(UPLOAD_ID)
            assertThat(firstValue.contentMd5()).isEqualTo("md5")
            assertThat(firstValue.artifactTypeAsString()).isEqualTo("type")
        }
    }

    @Test
    fun `test mapToCodeScanIssues`() {
        val recommendations = listOf(
            fakeListCodeScanFindingsResponse.codeScanFindings(),
            getFakeRecommendationsOnNonExistentFile()
        )
        val res = codeScanSessionSpy.mapToCodeScanIssues(recommendations)
        assertThat(res).hasSize(2)
    }

    @Test
    fun `test run() - happypath`() = runTest {
        val file = pyPsiFile.virtualFile.toNioPath().toFile()

        assertNotNull(pySession)
        val codeScanResponse = codeScanSessionSpy.run()
        assertThat(codeScanResponse).isInstanceOfSatisfying<CodeScanResponse.Success> {
            assertThat(it.issues).hasSize(2)
            assertThat(it.responseContext.payloadContext).isEqualTo(payloadContext)
            assertThat(it.responseContext.codeScanJobId).isEqualTo("jobId")
        }

        val inOrder = inOrder(codeScanSessionSpy)
        inOrder.verify(codeScanSessionSpy, times(1)).createUploadUrlAndUpload(eq(file), eq("SourceCode"), anyString())
        inOrder.verify(codeScanSessionSpy, times(1)).createCodeScan(eq(CodewhispererLanguage.Python.toString()), anyString())
        inOrder.verify(codeScanSessionSpy, times(1)).getCodeScan(any())
        inOrder.verify(codeScanSessionSpy, times(1)).listCodeScanFindings(eq("jobId"), eq(null))
    }

    @Test
    fun `test createPayload for files outside Project Root`() {
        val externalFile = projectRule.fixture.addFileToProject(
            "../test.java",
            """
            public class Addition {
                public static void main(String[] args) {
                    int a = 1;
                    int b = 2;
                    int c = a + b;
                    System.out.println(c);
                }
            }
            """
        )

        val sessionConfigSpy2 = spy(
            CodeScanSessionConfig.create(
                externalFile.virtualFile,
                project,
                CodeWhispererConstants.CodeAnalysisScope.FILE
            )
        )

        setupResponse(pyPsiFile.virtualFile.toNioPath().relativeTo(pySession.projectRoot.toNioPath()))

        val payload = sessionConfigSpy2.createPayload()
        assertNotNull(payload)
        val payloadZipFile = ZipFile(payload.srcZip)
        for (entry in payloadZipFile.entries()) {
            assertThat(!entry.name.startsWith(".."))
        }
    }

    @Test
    fun `unsupported languages file scan fail`() = runTest {
        val fileEditorManager = mock<FileEditorManager>()
        val selectedEditor = mock<FileEditor>()
        val editorList = mutableListOf(selectedEditor)

        whenever(fileEditorManager.selectedEditorWithRemotes).thenReturn(editorList)
        whenever(fileEditorManager.selectedEditor).thenReturn(selectedEditor)
        whenever(selectedEditor.file).thenReturn(ktPsiFile.virtualFile)

        scanManagerSpy.runCodeScan(CodeWhispererConstants.CodeAnalysisScope.FILE)
        // verify that function was run but none of the results/error handling methods were called.
        verify(scanManagerSpy, times(0)).updateFileIssues(any(), any())
        verify(scanManagerSpy, times(0)).handleError(any(), any(), any())
        verify(scanManagerSpy, times(0)).handleException(any(), any(), any())
    }

    @Test
    fun `test run() - file scans limit reached`() = runTest {
        assertNotNull(pySession)

        mockClient.stub {
            onGeneric { codeScanSessionSpy.createUploadUrlAndUpload(any(), any(), any()) }.thenThrow(
                CodeWhispererException.builder()
                    .message("File Scan Monthly Exceeded")
                    .requestId("abc123")
                    .statusCode(400)
                    .cause(RuntimeException("Something went wrong"))
                    .writableStackTrace(true)
                    .awsErrorDetails(
                        AwsErrorDetails.builder()
                            .errorCode("ThrottlingException")
                            .errorMessage("Maximum automatic file scan count reached for this month")
                            .serviceName("CodeWhispererService")
                            .build()
                    )
                    .build()
            )
        }
        val codeScanResponse = codeScanSessionSpy.run()
        assertThat(codeScanResponse).isInstanceOf<CodeScanResponse.Failure>()
        if (codeScanResponse is CodeScanResponse.Failure) {
            assertThat(codeScanResponse.failureReason).isInstanceOf<CodeWhispererException>()
            assertThat(codeScanResponse.failureReason.toString()).contains("File Scan Monthly Exceeded")
            assertThat(codeScanResponse.failureReason.cause.toString()).contains("java.lang.RuntimeException: Something went wrong")
        }
    }

    @Test
    fun `test run() - createCodeScan failed`() = runTest {
        mockClient.stub {
            onGeneric { createCodeScan(any(), any()) }.thenReturn(fakeCreateCodeScanResponseFailed)
        }

        val codeScanResponse = codeScanSessionSpy.run()
        assertThat(codeScanResponse).isInstanceOf<CodeScanResponse.Failure>()
        assertThat(codeScanResponse.responseContext.payloadContext).isEqualTo(payloadContext)
        assertThat((codeScanResponse as CodeScanResponse.Failure).failureReason).isInstanceOf<Exception>()
    }

    @Test
    fun `test run() - createCodeScan error`() = runTest {
        mockClient.stub {
            onGeneric { createCodeScan(any(), any()) }.thenThrow(CodeWhispererCodeScanServerException::class.java)
        }

        val codeScanResponse = codeScanSessionSpy.run()
        assertThat(codeScanResponse).isInstanceOf<CodeScanResponse.Failure>()
        assertThat(codeScanResponse.responseContext.payloadContext).isEqualTo(payloadContext)
        assertThat((codeScanResponse as CodeScanResponse.Failure).failureReason).isInstanceOf<CodeWhispererCodeScanServerException>()
    }

    @Test
    fun `test run() - getCodeScan failed`() = runTest {
        mockClient.stub {
            onGeneric { getCodeScan(any(), any()) }.thenReturn(fakeGetCodeScanResponseFailed)
        }

        val codeScanResponse = codeScanSessionSpy.run()
        assertThat(codeScanResponse).isInstanceOf<CodeScanResponse.Failure>()
        assertThat(codeScanResponse.responseContext.payloadContext).isEqualTo(payloadContext)
        assertThat((codeScanResponse as CodeScanResponse.Failure).failureReason).isInstanceOf<Exception>()
    }

    @Test
    fun `test run() - getCodeScan pending timeout`() = runTest {
        pySession.stub {
            onGeneric { overallJobTimeoutInSeconds() }.thenReturn(5)
        }
        mockClient.stub {
            onGeneric { getCodeScan(any(), any()) }.thenAnswer {
                Thread.sleep(TIMEOUT)
                fakeGetCodeScanResponsePending
            }
        }

        val codeScanResponse = codeScanSessionSpy.run()
        assertThat(codeScanResponse).isInstanceOf<CodeScanResponse.Failure>()
        assertThat(codeScanResponse.responseContext.payloadContext).isEqualTo(payloadContext)
        assertThat((codeScanResponse as CodeScanResponse.Failure).failureReason).isInstanceOf<WaiterTimeoutException>()
    }

    @Test
    fun `test run() - getCodeScan error`() = runTest {
        mockClient.stub {
            onGeneric { getCodeScan(any(), any()) }.thenThrow(CodeWhispererCodeScanServerException::class.java)
        }

        val codeScanResponse = codeScanSessionSpy.run()
        assertThat(codeScanResponse).isInstanceOf<CodeScanResponse.Failure>()
        assertThat(codeScanResponse.responseContext.payloadContext).isEqualTo(payloadContext)
        assertThat((codeScanResponse as CodeScanResponse.Failure).failureReason).isInstanceOf<CodeWhispererCodeScanServerException>()
    }

    @Test
    fun `test run() - listCodeScanFindings error`() = runTest {
        mockClient.stub {
            onGeneric { listCodeScanFindings(any(), any()) }.thenThrow(CodeWhispererCodeScanServerException::class.java)
        }

        val codeScanResponse = codeScanSessionSpy.run()
        assertThat(codeScanResponse).isInstanceOf<CodeScanResponse.Failure>()
        assertThat(codeScanResponse.responseContext.payloadContext).isEqualTo(payloadContext)
        assertThat((codeScanResponse as CodeScanResponse.Failure).failureReason).isInstanceOf<CodeWhispererCodeScanServerException>()
    }

    companion object {
        const val TIMEOUT = 10L * TOTAL_MILLIS_IN_SECOND
    }
}
