// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.codewhisperer.util

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.util.io.HttpRequests
import org.apache.commons.codec.digest.DigestUtils
import software.amazon.awssdk.awscore.exception.AwsServiceException
import software.amazon.awssdk.services.codewhispererruntime.model.CodeAnalysisUploadContext
import software.amazon.awssdk.services.codewhispererruntime.model.CodeFixUploadContext
import software.amazon.awssdk.services.codewhispererruntime.model.CreateUploadUrlRequest
import software.amazon.awssdk.services.codewhispererruntime.model.CreateUploadUrlResponse
import software.amazon.awssdk.services.codewhispererruntime.model.InternalServerException
import software.amazon.awssdk.services.codewhispererruntime.model.ThrottlingException
import software.amazon.awssdk.services.codewhispererruntime.model.UploadContext
import software.amazon.awssdk.services.codewhispererruntime.model.UploadIntent
import software.amazon.awssdk.utils.IoUtils
import software.aws.toolkits.core.utils.debug
import software.aws.toolkits.core.utils.getLogger
import software.aws.toolkits.jetbrains.core.AwsClientManager
import software.aws.toolkits.jetbrains.services.amazonq.RetryableOperation
import software.aws.toolkits.jetbrains.services.codewhisperer.codescan.CodeWhispererCodeScanServerException
import software.aws.toolkits.jetbrains.services.codewhisperer.codescan.CodeWhispererCodeScanSession.Companion.APPLICATION_ZIP
import software.aws.toolkits.jetbrains.services.codewhisperer.codescan.CodeWhispererCodeScanSession.Companion.AWS_KMS
import software.aws.toolkits.jetbrains.services.codewhisperer.codescan.CodeWhispererCodeScanSession.Companion.CONTENT_MD5
import software.aws.toolkits.jetbrains.services.codewhisperer.codescan.CodeWhispererCodeScanSession.Companion.CONTENT_TYPE
import software.aws.toolkits.jetbrains.services.codewhisperer.codescan.CodeWhispererCodeScanSession.Companion.SERVER_SIDE_ENCRYPTION
import software.aws.toolkits.jetbrains.services.codewhisperer.codescan.CodeWhispererCodeScanSession.Companion.SERVER_SIDE_ENCRYPTION_AWS_KMS_KEY_ID
import software.aws.toolkits.jetbrains.services.codewhisperer.codescan.CodeWhispererCodeScanSession.Companion.SERVER_SIDE_ENCRYPTION_CONTEXT
import software.aws.toolkits.jetbrains.services.codewhisperer.codescan.codeScanServerException
import software.aws.toolkits.jetbrains.services.codewhisperer.codescan.invalidSourceZipError
import software.aws.toolkits.jetbrains.services.codewhisperer.codetest.CodeTestException
import software.aws.toolkits.jetbrains.services.codewhisperer.credentials.CodeWhispererClientAdaptor
import software.aws.toolkits.jetbrains.services.cwc.controller.chat.telemetry.getStartUrl
import software.aws.toolkits.resources.message
import software.aws.toolkits.telemetry.AmazonqTelemetry
import software.aws.toolkits.telemetry.AmazonqUploadIntent
import software.aws.toolkits.telemetry.MetricResult
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.util.Base64
import software.aws.toolkits.jetbrains.services.codewhisperer.codetest.invalidSourceZipError as testGenerationInvalidSourceZipError

@Service
class CodeWhispererZipUploadManager(private val project: Project) {

    fun createUploadUrlAndUpload(
        zipFile: File,
        artifactType: String,
        taskType: CodeWhispererConstants.UploadTaskType,
        taskName: String,
        featureUseCase: CodeWhispererConstants.FeatureName,
    ): CreateUploadUrlResponse {
        val startTime = System.currentTimeMillis()
        var result: MetricResult = MetricResult.Succeeded
        var failureReason: String? = null
        var failureReasonDesc: String? = null
        var requestId: String? = null
        var requestServiceType: String? = null
        var httpStatusCode: String? = null
        var id2: String? = null
        try {
            //  Throw error if zipFile is invalid.
            if (!zipFile.exists()) {
                when (featureUseCase) {
                    CodeWhispererConstants.FeatureName.CODE_REVIEW -> invalidSourceZipError()
                    CodeWhispererConstants.FeatureName.TEST_GENERATION -> testGenerationInvalidSourceZipError()
                    else -> throw IllegalArgumentException("Unsupported feature case: $featureUseCase") // Adding else for safety check
                }
            }
            val fileMd5: String = Base64.getEncoder().encodeToString(DigestUtils.md5(FileInputStream(zipFile)))
            val createUploadUrlResponse = createUploadUrl(fileMd5, artifactType, taskType, taskName, featureUseCase)
            val url = createUploadUrlResponse.uploadUrl()
            LOG.debug { "$featureUseCase: Uploading $artifactType using the presigned URL." }

            val connection = uploadArtifactToS3(
                url,
                createUploadUrlResponse.uploadId(),
                zipFile,
                fileMd5,
                createUploadUrlResponse.kmsKeyArn(),
                createUploadUrlResponse.requestHeaders(),
                featureUseCase
            )
            requestId = connection?.getHeaderField("x-amz-request-id")
            id2 = connection?.getHeaderField("x-amz-id-2")
            return createUploadUrlResponse
        } catch (e: Exception) {
            result = MetricResult.Failed
            failureReason = e.javaClass.simpleName
            failureReasonDesc = e.message
            if (e is CodeWhispererCodeScanServerException) {
                requestId = e.requestId
                requestServiceType = e.requestServiceType
                httpStatusCode = e.httpStatusCode
                id2 = e.id2
            }
            throw e
        } finally {
            if (featureUseCase == CodeWhispererConstants.FeatureName.CODE_REVIEW) {
                LOG.info("Upload to S3 details: x-amz-request-id: $requestId and x-amz-id-2: $id2")
                AmazonqTelemetry.createUpload(
                    amazonqConversationId = "",
                    amazonqUploadIntent = if (taskType == CodeWhispererConstants.UploadTaskType.SCAN_PROJECT) {
                        AmazonqUploadIntent.FULLPROJECTSECURITYSCAN
                    } else {
                        AmazonqUploadIntent.AUTOMATICFILESECURITYSCAN
                    },
                    result = result,
                    reason = failureReason,
                    reasonDesc = failureReasonDesc,
                    duration = (System.currentTimeMillis() - startTime).toDouble(),
                    credentialStartUrl = getStartUrl(project),
                    requestId = requestId,
                    requestId2 = id2,
                    requestServiceType = requestServiceType,
                    httpStatusCode = httpStatusCode
                )
            }
        }
    }

    @Throws(IOException::class)
    fun uploadArtifactToS3(
        url: String,
        uploadId: String,
        fileToUpload: File,
        md5: String,
        kmsArn: String?,
        requestHeaders: Map<String, String>?,
        featureUseCase: CodeWhispererConstants.FeatureName,
    ) : HttpURLConnection? {
        var connection: HttpURLConnection? = null
        RetryableOperation<Unit>().execute(
            operation = {
                val uploadIdJson = """{"uploadId":"$uploadId"}"""
                HttpRequests.put(url, "application/zip").userAgent(AwsClientManager.getUserAgent()).tuner {
                    if (requestHeaders.isNullOrEmpty()) {
                        it.setRequestProperty(CONTENT_MD5, md5)
                        it.setRequestProperty(CONTENT_TYPE, APPLICATION_ZIP)
                        it.setRequestProperty(SERVER_SIDE_ENCRYPTION, AWS_KMS)
                        if (kmsArn?.isNotEmpty() == true) {
                            it.setRequestProperty(SERVER_SIDE_ENCRYPTION_AWS_KMS_KEY_ID, kmsArn)
                        }
                        it.setRequestProperty(SERVER_SIDE_ENCRYPTION_CONTEXT, Base64.getEncoder().encodeToString(uploadIdJson.toByteArray()))
                    } else {
                        requestHeaders.forEach { entry ->
                            it.setRequestProperty(entry.key, entry.value)
                        }
                    }
                }.connect {
                    connection = it.connection as HttpURLConnection
                    connection?.setFixedLengthStreamingMode(fileToUpload.length())
                    IoUtils.copy(fileToUpload.inputStream(), connection?.outputStream)
                }
            },
            isRetryable = { e ->
                when (e) {
                    is IOException -> true
                    else -> false
                }
            },
            errorHandler = { e, attempts ->
                val errorMessage = getTelemetryErrorMessage(e, featureUseCase)
                when (featureUseCase) {
                    CodeWhispererConstants.FeatureName.CODE_REVIEW ->
                        codeScanServerException(
                            "CreateUploadUrlException: $errorMessage",
                            connection?.getHeaderField("x-amz-request-id"),
                            connection?.getHeaderField("x-amz-id-2"),
                            "s3",
                            (e as? HttpRequests.HttpStatusException)?.statusCode.toString()
                        )
                    CodeWhispererConstants.FeatureName.TEST_GENERATION ->
                        throw CodeTestException(
                            "UploadTestArtifactToS3Error: $errorMessage",
                            "UploadTestArtifactToS3Error",
                            message("testgen.error.generic_technical_error_message")
                        )
                    else -> throw RuntimeException("$errorMessage (after $attempts attempts)")
                }
            }
        )
        return connection
    }

    fun createUploadUrl(
        md5Content: String,
        artifactType: String,
        uploadTaskType: CodeWhispererConstants.UploadTaskType,
        taskName: String,
        featureUseCase: CodeWhispererConstants.FeatureName,
    ): CreateUploadUrlResponse = RetryableOperation<CreateUploadUrlResponse>().execute(
        operation = {
            CodeWhispererClientAdaptor.getInstance(project).createUploadUrl(
                CreateUploadUrlRequest.builder()
                    .contentMd5(md5Content)
                    .artifactType(artifactType)
                    .uploadIntent(getUploadIntent(uploadTaskType))
                    .uploadContext(
                        // For UTG we don't need uploadContext but sending else case as UploadContext
                        if (uploadTaskType == CodeWhispererConstants.UploadTaskType.CODE_FIX) {
                            UploadContext.fromCodeFixUploadContext(CodeFixUploadContext.builder().codeFixName(taskName).build())
                        } else {
                            UploadContext.fromCodeAnalysisUploadContext(CodeAnalysisUploadContext.builder().codeScanName(taskName).build())
                        }
                    )
                    .build()
            )
        },
        isRetryable = { e ->
            e is ThrottlingException || e is InternalServerException
        },
        errorHandler = { e, attempts ->
            val errorMessage = getTelemetryErrorMessage(e, featureUseCase)
            when (featureUseCase) {
                CodeWhispererConstants.FeatureName.CODE_REVIEW ->
                    codeScanServerException(
                        "CreateUploadUrlException after $attempts attempts: $errorMessage",
                        requestId = (e as? AwsServiceException)?.requestId(),
                        httpStatusCode = (e as? AwsServiceException)?.statusCode().toString()
                    )

                CodeWhispererConstants.FeatureName.TEST_GENERATION ->
                    throw CodeTestException(
                        "CreateUploadUrlError after $attempts attempts: $errorMessage",
                        "CreateUploadUrlError",
                        message("testgen.error.generic_technical_error_message")
                    )

                else -> throw RuntimeException("$errorMessage (after $attempts attempts)")
            }
        }
    )

    private fun getUploadIntent(uploadTaskType: CodeWhispererConstants.UploadTaskType): UploadIntent = when (uploadTaskType) {
        CodeWhispererConstants.UploadTaskType.SCAN_FILE -> UploadIntent.AUTOMATIC_FILE_SECURITY_SCAN
        CodeWhispererConstants.UploadTaskType.SCAN_PROJECT -> UploadIntent.FULL_PROJECT_SECURITY_SCAN
        CodeWhispererConstants.UploadTaskType.UTG -> UploadIntent.UNIT_TESTS_GENERATION
        CodeWhispererConstants.UploadTaskType.CODE_FIX -> UploadIntent.CODE_FIX_GENERATION
    }

    companion object {
        fun getInstance(project: Project) = project.service<CodeWhispererZipUploadManager>()
        private val LOG = getLogger<CodeWhispererZipUploadManager>()
    }
}

fun getTelemetryErrorMessage(e: Exception, featureUseCase: CodeWhispererConstants.FeatureName): String = when {
    e.message?.contains("Resource not found.") == true -> "Resource not found."
    e.message?.contains("Maximum com.amazon.aws.codewhisperer.StartCodeAnalysis reached for this month.") == true -> message(
        "testgen.error.maximum_generations_reach"
    )
    e.message?.contains("Maximum com.amazon.aws.codewhisperer.runtime.StartTestGeneration reached for this month.") == true
    -> "Maximum com.amazon.aws.codewhisperer.runtime.StartTestGeneration reached for this month."
    e.message?.contains("Service returned HTTP status code 407") == true -> "Service returned HTTP status code 407"
    e.message?.contains("Improperly formed request") == true -> "Improperly formed request"
    e.message?.contains("Service returned HTTP status code 403") == true -> "Service returned HTTP status code 403"
    e.message?.contains("Service returned HTTP status code 503") == true -> "Service returned HTTP status code 503"
    e.message?.contains("invalid_grant: Invalid token provided") == true -> "invalid_grant: Invalid token provided"
    e.message?.contains("Connect timed out") == true -> "Unable to execute HTTP request: Connect timed out" // Error: Connect to host failed
    e.message?.contains("Encountered an unexpected error when processing the request, please try again.") == true ->
        "Encountered an unexpected error when processing the request, please try again."
    else -> e.message ?: when (featureUseCase) {
        CodeWhispererConstants.FeatureName.CODE_REVIEW -> message("codewhisperer.codescan.run_scan_error_telemetry")
        else -> message("testgen.message.failed")
    }
}
