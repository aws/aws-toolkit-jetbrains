// Copyright 2022 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.codewhisperer.codescan

import com.intellij.psi.PsiFile
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.apache.commons.codec.digest.DigestUtils
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Test
import org.mockito.internal.verification.Times
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doNothing
import org.mockito.kotlin.eq
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.spy
import org.mockito.kotlin.stub
import org.mockito.kotlin.verify
import software.amazon.awssdk.services.codewhisperer.model.CodeWhispererException
import software.amazon.awssdk.services.codewhisperer.model.CreateCodeScanRequest
import software.amazon.awssdk.services.codewhisperer.model.CreateUploadUrlRequest
import software.amazon.awssdk.services.codewhisperer.model.GetCodeScanRequest
import software.amazon.awssdk.services.codewhisperer.model.ListCodeScanFindingsRequest
import software.aws.toolkits.core.utils.WaiterTimeoutException
import software.aws.toolkits.jetbrains.services.codewhisperer.codescan.sessionconfig.CodeScanSessionConfig
import software.aws.toolkits.jetbrains.services.codewhisperer.codescan.sessionconfig.Payload
import software.aws.toolkits.jetbrains.services.codewhisperer.codescan.sessionconfig.PayloadContext
import software.aws.toolkits.jetbrains.services.codewhisperer.codescan.sessionconfig.PythonCodeScanSessionConfig
import software.aws.toolkits.jetbrains.services.codewhisperer.util.CodeWhispererConstants.TOTAL_MILLIS_IN_SECOND
import software.aws.toolkits.jetbrains.utils.isInstanceOf
import software.aws.toolkits.telemetry.CodewhispererLanguage
import java.io.File
import java.io.FileInputStream
import java.util.Base64
import kotlin.test.assertNotNull

class CodeWhispererCodeScanTest : CodeWhispererCodeScanTestBase() {
    internal lateinit var psifile: PsiFile
    internal lateinit var file: File
    private lateinit var sessionConfigSpy: PythonCodeScanSessionConfig
    private val payloadContext = PayloadContext(CodewhispererLanguage.Python, 1, 1, 10, 600, 200)
    private lateinit var codeScanSessionContext: CodeScanSessionContext
    private lateinit var codeScanSessionSpy: CodeWhispererCodeScanSession

    @Before
    override fun setup() {
        project = pythonProjectRule.project
        psifile = pythonProjectRule.fixture.addFileToProject(
            "/test.py",
            """import numpy as np
               import from module1 import helper
               
               def add(a, b):
                  return a + b
                  
            """.trimMargin()
        )
        file = psifile.virtualFile.toNioPath().toFile()

        sessionConfigSpy = spy(CodeScanSessionConfig.create(psifile.virtualFile, project) as PythonCodeScanSessionConfig)
        sessionConfigSpy.stub {
            onGeneric { sessionConfigSpy.createPayload() }.thenReturn(Payload(payloadContext, file))
        }

        super.setup()
        // Mock CodeWhispererClient needs to be setup before initializing CodeWhispererCodeScanSession
        codeScanSessionContext = CodeScanSessionContext(project, sessionConfigSpy)
        codeScanSessionSpy = spy(CodeWhispererCodeScanSession(codeScanSessionContext))
        doNothing().`when`(codeScanSessionSpy).uploadArtifactTOS3(any(), any(), any())
    }

    override fun setupCodeScanFindings(): String = defaultCodeScanFindings(psifile.virtualFile)

    @Test
    fun `test createUploadUrlAndUpload()`() {
        val fileMd5: String = Base64.getEncoder().encodeToString(DigestUtils.md5(FileInputStream(file)))
        codeScanSessionSpy.stub {
            onGeneric { codeScanSessionSpy.createUploadUrl(any(), any()) }
                .thenReturn(fakeCreateUploadUrlResponse)
        }

        codeScanSessionSpy.createUploadUrlAndUpload(file, "artifactType")

        val inOrder = inOrder(codeScanSessionSpy)
        inOrder.verify(codeScanSessionSpy).createUploadUrl(eq(fileMd5), eq("artifactType"))
        inOrder.verify(codeScanSessionSpy).uploadArtifactTOS3(eq(fakeCreateUploadUrlResponse.uploadUrl()), eq(file), eq(fileMd5))
    }

    @Test
    fun `test createUploadUrl()`() {
        val response = codeScanSessionSpy.createUploadUrl("md5", "type")

        argumentCaptor<CreateUploadUrlRequest>().apply {
            verify(mockClient, Times(1)).createUploadUrl(capture())
            assertThat(response.uploadUrl()).isEqualTo(s3endpoint)
            assertThat(response.uploadId()).isEqualTo(UPLOAD_ID)
            assertThat(firstValue.contentMd5()).isEqualTo("md5")
            assertThat(firstValue.artifactTypeAsString()).isEqualTo("type")
        }
    }

    @Test
    fun `test mapToCodeScanIssues`() {
        val recommendations = listOf(defaultCodeScanFindings(psifile.virtualFile), getFakeRecommendationsOnNonExistentFile())
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
        inOrder.verify(codeScanSessionSpy, Times(1)).createUploadUrlAndUpload(eq(file), eq("SourceCode"))
        inOrder.verify(codeScanSessionSpy, Times(1)).createCodeScan(eq(CodewhispererLanguage.Python.toString()))
        inOrder.verify(codeScanSessionSpy, Times(1)).getCodeScan(any())
        inOrder.verify(codeScanSessionSpy, Times(1)).listCodeScanFindings(eq("jobId"))
    }

    @Test
    fun `test run() - createCodeScan failed`() {
        mockClient.stub {
            onGeneric { createCodeScan(any<CreateCodeScanRequest>()) }.thenReturn(fakeCreateCodeScanResponseFailed)
        }

        runBlocking {
            val codeScanResponse = codeScanSessionSpy.run()
            assertThat(codeScanResponse).isInstanceOf<CodeScanResponse.Failure>()
            assertThat(codeScanResponse.responseContext.payloadContext).isEqualTo(payloadContext)
            assertThat((codeScanResponse as CodeScanResponse.Failure).failureReason).isInstanceOf<CodeWhispererCodeScanException>()
        }
    }

    @Test
    fun `test run() - createCodeScan error`() {
        mockClient.stub {
            onGeneric { createCodeScan(any<CreateCodeScanRequest>()) }.thenThrow(CodeWhispererException::class.java)
        }

        runBlocking {
            val codeScanResponse = codeScanSessionSpy.run()
            assertThat(codeScanResponse).isInstanceOf<CodeScanResponse.Failure>()
            assertThat(codeScanResponse.responseContext.payloadContext).isEqualTo(payloadContext)
            assertThat((codeScanResponse as CodeScanResponse.Failure).failureReason).isInstanceOf<CodeWhispererException>()
        }
    }

    @Test
    fun `test run() - getCodeScan failed`() {
        mockClient.stub {
            onGeneric { getCodeScan(any<GetCodeScanRequest>()) }.thenReturn(fakeGetCodeScanResponseFailed)
        }

        runBlocking {
            val codeScanResponse = codeScanSessionSpy.run()
            assertThat(codeScanResponse).isInstanceOf<CodeScanResponse.Failure>()
            assertThat(codeScanResponse.responseContext.payloadContext).isEqualTo(payloadContext)
            assertThat((codeScanResponse as CodeScanResponse.Failure).failureReason).isInstanceOf<CodeWhispererCodeScanException>()
        }
    }

    @Test
    fun `test run() - getCodeScan pending timeout`() {
        sessionConfigSpy.stub {
            onGeneric { overallJobTimeoutInSeconds() }.thenReturn(5)
        }
        mockClient.stub {
            onGeneric { getCodeScan(any<GetCodeScanRequest>()) }.thenAnswer {
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
            onGeneric { getCodeScan(any<GetCodeScanRequest>()) }.thenThrow(CodeWhispererException::class.java)
        }

        runBlocking {
            val codeScanResponse = codeScanSessionSpy.run()
            assertThat(codeScanResponse).isInstanceOf<CodeScanResponse.Failure>()
            assertThat(codeScanResponse.responseContext.payloadContext).isEqualTo(payloadContext)
            assertThat((codeScanResponse as CodeScanResponse.Failure).failureReason).isInstanceOf<CodeWhispererException>()
        }
    }

    @Test
    fun `test run() - listCodeScanFindings error`() {
        mockClient.stub {
            onGeneric { listCodeScanFindings(any<ListCodeScanFindingsRequest>()) }.thenThrow(CodeWhispererException::class.java)
        }

        runBlocking {
            val codeScanResponse = codeScanSessionSpy.run()
            assertThat(codeScanResponse).isInstanceOf<CodeScanResponse.Failure>()
            assertThat(codeScanResponse.responseContext.payloadContext).isEqualTo(payloadContext)
            assertThat((codeScanResponse as CodeScanResponse.Failure).failureReason).isInstanceOf<CodeWhispererException>()
        }
    }

    companion object {
        const val TIMEOUT = 10L * TOTAL_MILLIS_IN_SECOND
    }
}
