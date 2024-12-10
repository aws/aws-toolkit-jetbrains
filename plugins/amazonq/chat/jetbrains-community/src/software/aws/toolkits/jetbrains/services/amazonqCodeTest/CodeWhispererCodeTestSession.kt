// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonqCodeTest
import com.intellij.openapi.project.Project
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.time.withTimeout
import software.amazon.awssdk.services.codewhispererruntime.model.CreateUploadUrlResponse
import software.aws.toolkits.core.utils.debug
import software.aws.toolkits.core.utils.getLogger
import software.aws.toolkits.jetbrains.services.amazonqCodeTest.controller.CodeTestChatHelper
import software.aws.toolkits.jetbrains.services.amazonqCodeTest.messages.CodeTestChatMessageContent
import software.aws.toolkits.jetbrains.services.amazonqCodeTest.model.PreviousUTGIterationContext
import software.aws.toolkits.jetbrains.services.codewhisperer.codescan.sessionconfig.PayloadContext
import software.aws.toolkits.jetbrains.services.codewhisperer.codetest.sessionconfig.CodeTestSessionConfig
import software.aws.toolkits.jetbrains.services.codewhisperer.model.CreateUploadUrlServiceInvocationContext
import software.aws.toolkits.jetbrains.services.codewhisperer.util.CodeWhispererConstants
import software.aws.toolkits.jetbrains.services.codewhisperer.util.CodeWhispererConstants.TOTAL_BYTES_IN_KB
import software.aws.toolkits.jetbrains.services.codewhisperer.util.CodeWhispererConstants.TOTAL_MILLIS_IN_SECOND
import software.aws.toolkits.jetbrains.services.codewhisperer.util.CodeWhispererZipUploadManager
import software.aws.toolkits.jetbrains.services.cwc.messages.ChatMessageType
import software.aws.toolkits.jetbrains.utils.assertIsNonDispatchThread
import java.nio.file.Path
import java.time.Duration
import java.time.Instant
import java.util.UUID
import kotlin.coroutines.coroutineContext

// TODO: Refactor with CodeWhispererCodeScanSession code since both are about zip CreateUploadUrl logic
class CodeWhispererCodeTestSession(val sessionContext: CodeTestSessionContext) {
    private fun now() = Instant.now().toEpochMilli()

    /**
     * Run UTG sessions are follow steps:
     * 1. Zipping project
     * 2. Creating Upload url & Upload to S3 bucket
     * 3. StartTestGeneration API -> Get JobId
     * 4. GetTestGeneration API
     * 5. ExportResultsArchieve API
     */
    suspend fun run(codeTestChatHelper: CodeTestChatHelper, previousIterationContext: PreviousUTGIterationContext?): CodeTestResponseContext {
        try {
            assertIsNonDispatchThread()
            coroutineContext.ensureActive()

            val path = sessionContext.sessionConfig.getRelativePath()
                ?: throw RuntimeException("Can not determine current file path for adding unit tests")

            // Add card answer to show UTG in progress
            val testSummaryMessageId =
                if (previousIterationContext == null) {
                    codeTestChatHelper.addAnswer(
                        CodeTestChatMessageContent(
                            message = generateSummaryMessage(path.fileName.toString()),
                            type = ChatMessageType.AnswerStream
                        )
                    ).also {
                        // For streaming effect
                        codeTestChatHelper.updateAnswer(
                            CodeTestChatMessageContent(type = ChatMessageType.AnswerPart)
                        )
                        codeTestChatHelper.updateUI(
                            loadingChat = true,
                            promptInputDisabledState = true
                        )
                        if (it == null) {
                            throw RuntimeException("Can not add test summary card")
                        }
                    }
                } else {
                    // non-first iteration doesn't have a test summary card
                    null
                }

            val (payloadContext, sourceZip) = withTimeout(Duration.ofSeconds(sessionContext.sessionConfig.createPayloadTimeoutInSeconds())) {
                sessionContext.sessionConfig.createPayload()
            }

            LOG.debug {
                "Total size of source payload in KB: ${payloadContext.srcPayloadSize * 1.0 / TOTAL_BYTES_IN_KB} \n" +
                    "Total size of build payload in KB: ${(payloadContext.buildPayloadSize ?: 0) * 1.0 / TOTAL_BYTES_IN_KB} \n" +
                    "Total size of source zip file in KB: ${payloadContext.srcZipFileSize * 1.0 / TOTAL_BYTES_IN_KB} \n" +
                    "Total number of lines included: ${payloadContext.totalLines} \n" +
                    "Total number of files included in payload: ${payloadContext.totalFiles} \n" +
                    "Total time taken for creating payload: ${payloadContext.totalTimeInMilliseconds * 1.0 / TOTAL_MILLIS_IN_SECOND} seconds\n" +
                    "Payload context language: ${payloadContext.language}"
            }

            //  2 & 3. CreateUploadURL and upload the context.
            val artifactsUploadStartTime = now()
            val taskName = UUID.randomUUID().toString()
            val sourceZipUploadResponse =
                CodeWhispererZipUploadManager.getInstance(sessionContext.project).createUploadUrlAndUpload(
                    sourceZip,
                    "SourceCode",
                    CodeWhispererConstants.UploadTaskType.UTG,
                    taskName
                )

            sourceZipUploadResponse.uploadId()

            LOG.debug {
                "Successfully uploaded source zip to s3: " +
                    "Upload id: ${sourceZipUploadResponse.uploadId()} " +
                    "Request id: ${sourceZipUploadResponse.responseMetadata().requestId()}"
            }
            val artifactsUploadDuration = now() - artifactsUploadStartTime

            val codeTestResponseContext = CodeTestResponseContext(
                payloadContext,
                CreateUploadUrlServiceInvocationContext(artifactsUploadDuration = artifactsUploadDuration),
                path,
                sourceZipUploadResponse,
                testSummaryMessageId
            )
            // TODO send telemetry for upload duration

            return codeTestResponseContext
        } catch (e: Exception) {
            LOG.debug(e) { "Error when creating tests for the current file" }
            throw e
        }
    }

    companion object {
        private val LOG = getLogger<CodeWhispererCodeTestSession>()
    }
}

sealed class CodeTestResponse {
    abstract val responseContext: CodeTestResponseContext
    data class Success(val message: String, override val responseContext: CodeTestResponseContext) : CodeTestResponse()

    data class Error(val errorMessage: String, override val responseContext: CodeTestResponseContext) : CodeTestResponse()
}

data class CodeTestSessionContext(
    val project: Project,
    val sessionConfig: CodeTestSessionConfig,
)

data class CodeTestResponseContext(
    val payloadContext: PayloadContext,
    val serviceInvocationContext: CreateUploadUrlServiceInvocationContext,
    val currentFileRelativePath: Path,
    val createUploadUrlResponse: CreateUploadUrlResponse,
    val testSummaryMessageId: String?,
    val reason: String? = null,
)
