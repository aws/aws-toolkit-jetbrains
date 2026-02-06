// Copyright 2022 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.codewhisperer.codescan

import com.intellij.psi.PsiFile
import kotlinx.coroutines.test.runTest
import org.apache.commons.codec.digest.DigestUtils
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.ArgumentMatchers.anyString
import org.mockito.internal.verification.Times
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.isNull
import org.mockito.kotlin.spy
import org.mockito.kotlin.stub
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import software.amazon.awssdk.awscore.exception.AwsErrorDetails
import software.amazon.awssdk.services.codewhispererruntime.model.CodeWhispererRuntimeException
import software.amazon.awssdk.services.codewhispererruntime.model.CreateUploadUrlRequest
import software.aws.toolkits.core.utils.WaiterTimeoutException
import software.aws.toolkits.jetbrains.services.codewhisperer.codescan.sessionconfig.CodeScanSessionConfig
import software.aws.toolkits.jetbrains.services.codewhisperer.codescan.sessionconfig.Payload
import software.aws.toolkits.jetbrains.services.codewhisperer.codescan.sessionconfig.PayloadContext
import software.aws.toolkits.jetbrains.services.codewhisperer.util.CodeWhispererConstants
import software.aws.toolkits.jetbrains.services.codewhisperer.util.CodeWhispererConstants.TOTAL_MILLIS_IN_SECOND
import software.aws.toolkits.jetbrains.services.codewhisperer.util.getTelemetryErrorMessage
import software.aws.toolkits.jetbrains.utils.isInstanceOf
import software.aws.toolkits.jetbrains.utils.isInstanceOfSatisfying
import software.aws.toolkits.jetbrains.utils.rules.PythonCodeInsightTestFixtureRule
import software.aws.toolkits.telemetry.CodewhispererLanguage
import java.io.File
import java.io.FileInputStream
import java.util.Base64
import java.util.UUID
import kotlin.io.path.relativeTo
import kotlin.test.assertNotNull

class CodeWhispererCodeScanTest : CodeWhispererCodeScanTestBase(PythonCodeInsightTestFixtureRule()) {
    private lateinit var psifile: PsiFile
    private lateinit var file: File
    private lateinit var sessionConfigSpy: CodeScanSessionConfig
    private val payloadContext = PayloadContext(CodewhispererLanguage.Python, 1, 1, 10, listOf(), 600, 200)
    private lateinit var codeScanSessionContext: CodeScanSessionContext
    private lateinit var codeScanSessionSpy: CodeWhispererCodeScanSession
    private val codeScanName = UUID.randomUUID().toString()

    @Before
    override fun setup() {
        super.setup()
        psifile = projectRule.fixture.addFileToProject(
            "/test.py",
            """import numpy as np
               import from module1 import helper
               
               def add(a, b):
                  return a + b
                  
            """.trimMargin()
        )
        file = psifile.virtualFile.toNioPath().toFile()

        sessionConfigSpy = spy(
            CodeScanSessionConfig.create(
                psifile.virtualFile,
                project,
                CodeWhispererConstants.CodeAnalysisScope.PROJECT,
                false
            )
        )
        setupResponse(psifile.virtualFile.toNioPath().relativeTo(sessionConfigSpy.projectRoot.toNioPath()))

        sessionConfigSpy.stub {
            onGeneric { sessionConfigSpy.createPayload() }.thenReturn(Payload(payloadContext, file))
        }

        // Mock CodeWhispererClient needs to be setup before initializing CodeWhispererCodeScanSession
        codeScanSessionContext = CodeScanSessionContext(project, sessionConfigSpy, CodeWhispererConstants.CodeAnalysisScope.PROJECT)
        codeScanSessionSpy = spy(CodeWhispererCodeScanSession(codeScanSessionContext))

        mockClient.stub {
            onGeneric { createUploadUrl(any()) }.thenReturn(fakeCreateUploadUrlResponse)
            onGeneric { createCodeScan(any()) }.thenReturn(fakeCreateCodeScanResponse)
            onGeneric { getCodeScan(any()) }.thenReturn(fakeGetCodeScanResponse)
            onGeneric { listCodeScanFindings(any()) }.thenReturn(fakeListCodeScanFindingsResponse)
        }
    }

    @Test
    fun `test createUploadUrlAndUpload()`() {
        val fileMd5: String = Base64.getEncoder().encodeToString(DigestUtils.md5(FileInputStream(file)))
        zipUploadManagerSpy.stub {
            onGeneric { zipUploadManagerSpy.createUploadUrl(any(), any(), any(), any(), any()) }
                .thenReturn(fakeCreateUploadUrlResponse)
        }

        zipUploadManagerSpy.createUploadUrlAndUpload(
            file,
            "artifactType",
            CodeWhispererConstants.UploadTaskType.SCAN_FILE,
            codeScanName,
            CodeWhispererConstants.FeatureName.CODE_REVIEW
        )

        val inOrder = inOrder(zipUploadManagerSpy)
        inOrder.verify(zipUploadManagerSpy).createUploadUrl(eq(fileMd5), eq("artifactType"), any(), any(), any())
        inOrder.verify(zipUploadManagerSpy).uploadArtifactToS3(
            eq(fakeCreateUploadUrlResponse.uploadUrl()),
            eq(fakeCreateUploadUrlResponse.uploadId()),
            eq(file),
            eq(fileMd5),
            isNull(),
            any(),
            any()
        )
    }

    @Test
    fun `test createUploadUrlAndUpload() with invalid source zip file`() {
        val invalidZipFile = File("/path/file.zip")

        assertThrows<CodeWhispererCodeScanException> {
            zipUploadManagerSpy.createUploadUrlAndUpload(
                invalidZipFile,
                "artifactType",
                CodeWhispererConstants.UploadTaskType.SCAN_FILE,
                codeScanName,
                CodeWhispererConstants.FeatureName.CODE_REVIEW
            )
        }
    }

    @Test
    fun `test createUploadUrl()`() {
        val response = zipUploadManagerSpy.createUploadUrl(
            "md5",
            "type",
            CodeWhispererConstants.UploadTaskType.SCAN_FILE,
            codeScanName,
            featureUseCase = CodeWhispererConstants.FeatureName.CODE_REVIEW
        )

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
            fakeListCodeScanFindingsResponse.codeAnalysisFindings(),
            getFakeRecommendationsOnNonExistentFile()
        )
        val res = codeScanSessionSpy.mapToCodeScanIssues(recommendations, project, "jobId")
        assertThat(res).hasSize(2)
    }

    @Test
    fun `test mapToCodeScanIssues - handles index out of bounds`() {
        val recommendations = listOf(
            fakeListCodeScanFindingsOutOfBoundsIndexResponse.codeAnalysisFindings(),
        )
        val res = codeScanSessionSpy.mapToCodeScanIssues(recommendations, project, "jobId")
        assertThat(res).hasSize(1)
    }

    @Test
    fun `test getTelemetryErrorMessage should return the correct error message`() {
        val exceptions = listOf(
            Exception("Resource not found."),
            Exception("Service returned HTTP status code 407"),
            Exception("Service returned HTTP status code 403"),
            Exception("invalid_grant: Invalid token provided"),
            Exception("Connect timed out"),
            Exception("Encountered an unexpected error when processing the request, please try again."),
            Exception("Some other error message"),
            Exception("Improperly formed request")
        )

        val expectedMessages = listOf(
            "Resource not found.",
            "Service returned HTTP status code 407",
            "Service returned HTTP status code 403",
            "invalid_grant: Invalid token provided",
            "Unable to execute HTTP request: Connect timed out",
            "Encountered an unexpected error when processing the request, please try again.",
            "Some other error message",
            "Improperly formed request"
        )

        exceptions.forEachIndexed { index, exception ->
            val actualMessage = getTelemetryErrorMessage(exception, featureUseCase = CodeWhispererConstants.FeatureName.CODE_REVIEW)
            assertThat(expectedMessages[index]).isEqualTo(actualMessage)
        }
    }

    @Test
    fun `test run() - happypath`() = runTest {
        assertNotNull(sessionConfigSpy)
        val codeScanResponse = codeScanSessionSpy.run()
        assertThat(codeScanResponse).isInstanceOfSatisfying<CodeScanResponse.Success> {
            assertThat(it.issues).hasSize(2)
            assertThat(it.responseContext.payloadContext).isEqualTo(payloadContext)
            assertThat(it.responseContext.codeScanJobId).isEqualTo("jobId")
        }

        verify(zipUploadManagerSpy, times(1)).createUploadUrlAndUpload(eq(file), eq("SourceCode"), any(), anyString(), any())
        val inOrder = inOrder(codeScanSessionSpy)
        inOrder.verify(codeScanSessionSpy, Times(1)).createCodeScan(eq(CodewhispererLanguage.Python.toString()), anyString())
        inOrder.verify(codeScanSessionSpy, Times(1)).getCodeScan(any())
        inOrder.verify(codeScanSessionSpy, Times(1)).listCodeScanFindings(eq("jobId"), isNull())
    }

    @Test
    fun `test run() - code scans limit reached`() = runTest {
        assertNotNull(sessionConfigSpy)

        mockClient.stub {
            onGeneric { zipUploadManagerSpy.createUploadUrlAndUpload(any(), any(), any(), any(), any()) }.thenThrow(
                CodeWhispererRuntimeException.builder()
                    .message("Project Review Monthly Exceeded")
                    .requestId("abc123")
                    .statusCode(400)
                    .cause(RuntimeException("Something went wrong"))
                    .writableStackTrace(true)
                    .awsErrorDetails(
                        AwsErrorDetails.builder()
                            .errorCode("ThrottlingException")
                            .errorMessage("Maximum full project scan count reached for this month")
                            .serviceName("CodeWhispererService")
                            .build()
                    )
                    .build()
            )
        }

        val codeScanResponse = codeScanSessionSpy.run()
        assertThat(codeScanResponse).isInstanceOf<CodeScanResponse.Failure>()
        if (codeScanResponse is CodeScanResponse.Failure) {
            assertThat(codeScanResponse.failureReason).isInstanceOf<CodeWhispererRuntimeException>()
            assertThat(codeScanResponse.failureReason.toString()).contains("Project Review Monthly Exceeded")
            assertThat(codeScanResponse.failureReason.cause.toString()).contains("java.lang.RuntimeException: Something went wrong")
        }
    }

    @Test
    fun `test run() - createCodeScan failed`() = runTest {
        mockClient.stub {
            onGeneric { createCodeScan(any()) }.thenReturn(fakeCreateCodeScanResponseFailed)
        }

        val codeScanResponse = codeScanSessionSpy.run()
        assertThat(codeScanResponse).isInstanceOf<CodeScanResponse.Failure>()
        assertThat(codeScanResponse.responseContext.payloadContext).isEqualTo(payloadContext)
        assertThat((codeScanResponse as CodeScanResponse.Failure).failureReason).isInstanceOf<Exception>()
    }

    @Test
    fun `test run() - createCodeScan error`() = runTest {
        mockClient.stub {
            onGeneric { createCodeScan(any()) }.thenThrow(CodeWhispererCodeScanServerException::class.java)
        }

        val codeScanResponse = codeScanSessionSpy.run()
        assertThat(codeScanResponse).isInstanceOf<CodeScanResponse.Failure>()
        assertThat(codeScanResponse.responseContext.payloadContext).isEqualTo(payloadContext)
        assertThat((codeScanResponse as CodeScanResponse.Failure).failureReason).isInstanceOf<CodeWhispererCodeScanServerException>()
    }

    @Test
    fun `test run() - getCodeScan failed`() = runTest {
        mockClient.stub {
            onGeneric { getCodeScan(any()) }.thenReturn(fakeGetCodeScanResponseFailed)
        }

        val codeScanResponse = codeScanSessionSpy.run()
        assertThat(codeScanResponse).isInstanceOf<CodeScanResponse.Failure>()
        assertThat(codeScanResponse.responseContext.payloadContext).isEqualTo(payloadContext)
        assertThat((codeScanResponse as CodeScanResponse.Failure).failureReason).isInstanceOf<Exception>()
    }

    @Test
    fun `test run() - getCodeScan pending timeout`() = runTest {
        sessionConfigSpy.stub {
            onGeneric { overallJobTimeoutInSeconds() }.thenReturn(5)
        }
        mockClient.stub {
            onGeneric { getCodeScan(any()) }.thenAnswer {
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
            onGeneric { getCodeScan(any()) }.thenThrow(CodeWhispererCodeScanServerException::class.java)
        }

        val codeScanResponse = codeScanSessionSpy.run()
        assertThat(codeScanResponse).isInstanceOf<CodeScanResponse.Failure>()
        assertThat(codeScanResponse.responseContext.payloadContext).isEqualTo(payloadContext)
        assertThat((codeScanResponse as CodeScanResponse.Failure).failureReason).isInstanceOf<CodeWhispererCodeScanServerException>()
    }

    @Test
    fun `test run() - listCodeScanFindings error`() = runTest {
        mockClient.stub {
            onGeneric { listCodeScanFindings(any()) }.thenThrow(CodeWhispererCodeScanServerException::class.java)
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
