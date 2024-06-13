// Copyright 2022 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.codewhisperer.codescan

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.intellij.testFramework.replaceService
import com.intellij.testFramework.runInEdtAndWait
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.apache.commons.codec.digest.DigestUtils
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito
import org.mockito.Mockito.mock
import org.mockito.Mockito.times
import org.mockito.Mockito.`when`
import org.mockito.internal.verification.Times
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doNothing
import org.mockito.kotlin.eq
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.isNull
import org.mockito.kotlin.reset
import org.mockito.kotlin.spy
import org.mockito.kotlin.stub
import org.mockito.kotlin.verify
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
import software.aws.toolkits.jetbrains.utils.rules.PythonCodeInsightTestFixtureRule
import software.aws.toolkits.telemetry.CodewhispererLanguage
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.util.Base64
import java.util.Scanner
import java.util.UUID
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
import kotlin.io.path.relativeTo
import kotlin.test.assertNotNull

class CodeWhispererCodeFileScanTest : CodeWhispererCodeScanTestBase(PythonCodeInsightTestFixtureRule()) {
    private lateinit var psifile: PsiFile
    private lateinit var psifile2: PsiFile
    private lateinit var psifile3: PsiFile
    private lateinit var psifile4: PsiFile
    private lateinit var file: File
    private lateinit var file2: File
    private lateinit var file3: File
    private lateinit var file4: File
    private lateinit var virtualFile3: VirtualFile
    private lateinit var virtualFile4: VirtualFile
    private lateinit var sessionConfigSpy: CodeScanSessionConfig
    private lateinit var sessionConfigSpy2: CodeScanSessionConfig
    private val payloadContext = PayloadContext(CodewhispererLanguage.Python, 1, 1, 10, listOf(), 600, 200)
    private lateinit var codeScanSessionContext: CodeScanSessionContext
    private lateinit var codeScanSessionSpy: CodeWhispererCodeScanSession
    private val codeScanName = UUID.randomUUID().toString()

    @Before
    override fun setup() {
        super.setup()

        psifile2 = projectRule.fixture.addFileToProject(
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
        file2 = psifile2.virtualFile.toNioPath().toFile()

        psifile = projectRule.fixture.configureByText(
            "/test.py",
            """import numpy as np
               import from module1 import helper
               
               def add(a, b):
                  return a + b
                  
            """.trimMargin()
        )
        file = psifile.virtualFile.toNioPath().toFile()

        psifile3 = projectRule.fixture.addFileToProject(
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
        virtualFile3 = psifile3.virtualFile
        file3 = virtualFile3.toNioPath().toFile()

        psifile4 = projectRule.fixture.addFileToProject(
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
        virtualFile4 = psifile4.virtualFile
        file4 = virtualFile4.toNioPath().toFile()

        sessionConfigSpy = spy(
            CodeScanSessionConfig.create(
                psifile.virtualFile,
                project,
                CodeWhispererConstants.CodeAnalysisScope.FILE
            )
        )

        sessionConfigSpy2 = spy(
            CodeScanSessionConfig.create(
                virtualFile4,
                project,
                CodeWhispererConstants.CodeAnalysisScope.FILE
            )
        )

        setupResponse(psifile.virtualFile.toNioPath().relativeTo(sessionConfigSpy.projectRoot.toNioPath()))

        sessionConfigSpy.stub {
            onGeneric { sessionConfigSpy.createPayload() }.thenReturn(Payload(payloadContext, file))
        }

        // Mock CodeWhispererClient needs to be setup before initializing CodeWhispererCodeScanSession
        codeScanSessionContext = CodeScanSessionContext(project, sessionConfigSpy, CodeWhispererConstants.CodeAnalysisScope.FILE)
        codeScanSessionSpy = spy(CodeWhispererCodeScanSession(codeScanSessionContext))
        doNothing().`when`(codeScanSessionSpy).uploadArtifactToS3(any(), any(), any(), any(), isNull(), any())

        mockClient.stub {
            onGeneric { createUploadUrl(any()) }.thenReturn(fakeCreateUploadUrlResponse)
            onGeneric { createCodeScan(any(), any()) }.thenReturn(fakeCreateCodeScanResponse)
            onGeneric { getCodeScan(any(), any()) }.thenReturn(fakeGetCodeScanResponse)
            onGeneric { listCodeScanFindings(any(), any()) }.thenReturn(fakeListCodeScanFindingsResponse)
        }
    }

    @Test
    fun `test createUploadUrlAndUpload()`() {
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
    fun `test run() - happypath`() {
        assertNotNull(sessionConfigSpy)
        runBlocking {
            val codeScanResponse = codeScanSessionSpy.run()
            assertThat(codeScanResponse).isInstanceOf<CodeScanResponse.Success>()
            assertThat(codeScanResponse.issues).hasSize(2)
            assertThat(codeScanResponse.responseContext.payloadContext).isEqualTo(payloadContext)
            assertThat(codeScanResponse.responseContext.codeScanJobId).isEqualTo("jobId")
        }

        val inOrder = inOrder(codeScanSessionSpy)
        inOrder.verify(codeScanSessionSpy, Times(1)).createUploadUrlAndUpload(eq(file), eq("SourceCode"), anyString())
        inOrder.verify(codeScanSessionSpy, Times(1)).createCodeScan(eq(CodewhispererLanguage.Python.toString()), anyString())
        inOrder.verify(codeScanSessionSpy, Times(1)).getCodeScan(any())
        inOrder.verify(codeScanSessionSpy, Times(1)).listCodeScanFindings(eq("jobId"), eq(null))
    }

    @Test
    fun `test createPayload for files outside Project Root`() {
        val payload = sessionConfigSpy2.createPayload()
        assertNotNull(payload)
        val payloadZipFile = ZipFile(payload.srcZip)
        for (entry in payloadZipFile.entries()) {
            assertThat(!entry.name.startsWith(".."))
        }
    }

    @Test
    fun `unsupported languages file scan fail`() {
        scanManagerSpy = Mockito.spy(CodeWhispererCodeScanManager.getInstance(projectRule.project))
        projectRule.project.replaceService(CodeWhispererCodeScanManager::class.java, scanManagerSpy, disposableRule.disposable)

        val fileEditorManager = mock(FileEditorManager::class.java)
        val selectedEditor = mock(FileEditor::class.java)
        val editorList: MutableList<FileEditor> = mutableListOf(selectedEditor)

        `when`(fileEditorManager.selectedEditorWithRemotes).thenReturn(editorList)
        `when`(fileEditorManager.selectedEditor).thenReturn(selectedEditor)
        `when`(selectedEditor.file).thenReturn(virtualFile3)

        runBlocking {
            scanManagerSpy.runCodeScan(CodeWhispererConstants.CodeAnalysisScope.FILE)
        }
        // verify that function was run but none of the results/error handling methods were called.
        verify(scanManagerSpy, times(0)).updateFileIssues(any(), any())
        verify(scanManagerSpy, times(0)).handleError(any(), any(), any())
        verify(scanManagerSpy, times(0)).handleException(any(), any(), any())
    }

    @Test
    fun `test run() - file scans limit reached`() {
        assertNotNull(sessionConfigSpy)

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
        runBlocking {
            val codeScanResponse = codeScanSessionSpy.run()
            assertThat(codeScanResponse).isInstanceOf<CodeScanResponse.Failure>()
            if (codeScanResponse is CodeScanResponse.Failure) {
                assertThat(codeScanResponse.failureReason).isInstanceOf<CodeWhispererException>()
                assertThat(codeScanResponse.failureReason.toString()).contains("File Scan Monthly Exceeded")
                assertThat(codeScanResponse.failureReason.cause.toString()).contains("java.lang.RuntimeException: Something went wrong")
            }
        }
    }

    @Test
    fun `test run() - createCodeScan failed`() {
        mockClient.stub {
            onGeneric { createCodeScan(any(), any()) }.thenReturn(fakeCreateCodeScanResponseFailed)
        }

        runBlocking {
            val codeScanResponse = codeScanSessionSpy.run()
            assertThat(codeScanResponse).isInstanceOf<CodeScanResponse.Failure>()
            assertThat(codeScanResponse.responseContext.payloadContext).isEqualTo(payloadContext)
            assertThat((codeScanResponse as CodeScanResponse.Failure).failureReason).isInstanceOf<Exception>()
        }
    }

    @Test
    fun `test run() - createCodeScan error`() {
        mockClient.stub {
            onGeneric { createCodeScan(any(), any()) }.thenThrow(CodeWhispererCodeScanServerException::class.java)
        }

        runBlocking {
            val codeScanResponse = codeScanSessionSpy.run()
            assertThat(codeScanResponse).isInstanceOf<CodeScanResponse.Failure>()
            assertThat(codeScanResponse.responseContext.payloadContext).isEqualTo(payloadContext)
            assertThat((codeScanResponse as CodeScanResponse.Failure).failureReason).isInstanceOf<CodeWhispererCodeScanServerException>()
        }
    }

    @Test
    fun `test run() - getCodeScan failed`() {
        mockClient.stub {
            onGeneric { getCodeScan(any(), any()) }.thenReturn(fakeGetCodeScanResponseFailed)
        }

        runBlocking {
            val codeScanResponse = codeScanSessionSpy.run()
            assertThat(codeScanResponse).isInstanceOf<CodeScanResponse.Failure>()
            assertThat(codeScanResponse.responseContext.payloadContext).isEqualTo(payloadContext)
            assertThat((codeScanResponse as CodeScanResponse.Failure).failureReason).isInstanceOf<Exception>()
        }
    }

    @Test
    fun `test run() - getCodeScan pending timeout`() {
        sessionConfigSpy.stub {
            onGeneric { overallJobTimeoutInSeconds() }.thenReturn(5)
        }
        mockClient.stub {
            onGeneric { getCodeScan(any(), any()) }.thenAnswer {
                runBlocking {
                    delay(TIMEOUT)
                }
                fakeGetCodeScanResponsePending
            }
        }

        runBlocking {
            val codeScanResponse = codeScanSessionSpy.run()
            assertThat(codeScanResponse).isInstanceOf<CodeScanResponse.Failure>()
            assertThat(codeScanResponse.responseContext.payloadContext).isEqualTo(payloadContext)
            assertThat((codeScanResponse as CodeScanResponse.Failure).failureReason).isInstanceOf<WaiterTimeoutException>()
        }
    }

    @Test
    fun `test run() - getCodeScan error`() {
        mockClient.stub {
            onGeneric { getCodeScan(any(), any()) }.thenThrow(CodeWhispererCodeScanServerException::class.java)
        }

        runBlocking {
            val codeScanResponse = codeScanSessionSpy.run()
            assertThat(codeScanResponse).isInstanceOf<CodeScanResponse.Failure>()
            assertThat(codeScanResponse.responseContext.payloadContext).isEqualTo(payloadContext)
            assertThat((codeScanResponse as CodeScanResponse.Failure).failureReason).isInstanceOf<CodeWhispererCodeScanServerException>()
        }
    }

    @Test
    fun `test run() - listCodeScanFindings error`() {
        mockClient.stub {
            onGeneric { listCodeScanFindings(any(), any()) }.thenThrow(CodeWhispererCodeScanServerException::class.java)
        }

        runBlocking {
            val codeScanResponse = codeScanSessionSpy.run()
            assertThat(codeScanResponse).isInstanceOf<CodeScanResponse.Failure>()
            assertThat(codeScanResponse.responseContext.payloadContext).isEqualTo(payloadContext)
            assertThat((codeScanResponse as CodeScanResponse.Failure).failureReason).isInstanceOf<CodeWhispererCodeScanServerException>()
        }
    }

    @Test
    fun `test zipFile uses latest document changes`() {
        reset(sessionConfigSpy)
        runInEdtAndWait {
            WriteCommandAction.runWriteCommandAction(project) {
                projectRule.fixture.editor.document.insertString(0, "line inserted at beginning of file\n")
            }
        }

        val payload = sessionConfigSpy.createPayload()
        val bufferedInputStream = BufferedInputStream(payload.srcZip.inputStream())
        val zis = ZipInputStream(bufferedInputStream)
        zis.nextEntry
        val scanner = Scanner(zis)
        var contents = ""
        while (scanner.hasNextLine()) {
            contents += scanner.nextLine() + "\n"
        }

        val expectedContents = """line inserted at beginning of file
import numpy as np
               import from module1 import helper
               
               def add(a, b):
                  return a + b
                  
"""

        assertThat(contents).isEqualTo(expectedContents)
    }

    companion object {
        const val TIMEOUT = 10L * TOTAL_MILLIS_IN_SECOND
    }
}
