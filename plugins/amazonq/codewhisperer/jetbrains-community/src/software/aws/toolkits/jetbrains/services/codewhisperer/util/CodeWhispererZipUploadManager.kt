// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.codewhisperer.util

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.util.io.HttpRequests
import org.apache.commons.codec.digest.DigestUtils
import software.amazon.awssdk.services.codewhispererruntime.model.CodeAnalysisUploadContext
import software.amazon.awssdk.services.codewhispererruntime.model.CodeFixUploadContext
import software.amazon.awssdk.services.codewhispererruntime.model.CreateUploadUrlRequest
import software.amazon.awssdk.services.codewhispererruntime.model.CreateUploadUrlResponse
import software.amazon.awssdk.services.codewhispererruntime.model.UploadContext
import software.amazon.awssdk.services.codewhispererruntime.model.UploadIntent
import software.amazon.awssdk.utils.IoUtils
import software.aws.toolkits.core.utils.debug
import software.aws.toolkits.core.utils.getLogger
import software.aws.toolkits.jetbrains.core.AwsClientManager
import software.aws.toolkits.jetbrains.services.codewhisperer.codescan.CodeWhispererCodeScanSession.Companion.APPLICATION_ZIP
import software.aws.toolkits.jetbrains.services.codewhisperer.codescan.CodeWhispererCodeScanSession.Companion.AWS_KMS
import software.aws.toolkits.jetbrains.services.codewhisperer.codescan.CodeWhispererCodeScanSession.Companion.CONTENT_MD5
import software.aws.toolkits.jetbrains.services.codewhisperer.codescan.CodeWhispererCodeScanSession.Companion.CONTENT_TYPE
import software.aws.toolkits.jetbrains.services.codewhisperer.codescan.CodeWhispererCodeScanSession.Companion.SERVER_SIDE_ENCRYPTION
import software.aws.toolkits.jetbrains.services.codewhisperer.codescan.CodeWhispererCodeScanSession.Companion.SERVER_SIDE_ENCRYPTION_AWS_KMS_KEY_ID
import software.aws.toolkits.jetbrains.services.codewhisperer.codescan.CodeWhispererCodeScanSession.Companion.SERVER_SIDE_ENCRYPTION_CONTEXT
import software.aws.toolkits.jetbrains.services.codewhisperer.codescan.invalidSourceZipError
import software.aws.toolkits.jetbrains.services.codewhisperer.credentials.CodeWhispererClientAdaptor
import software.aws.toolkits.resources.message
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.util.Base64

@Service
class CodeWhispererZipUploadManager(private val project: Project) {

    fun createUploadUrlAndUpload(
        zipFile: File,
        artifactType: String,
        taskType: CodeWhispererConstants.UploadTaskType,
        taskName: String,
    ): CreateUploadUrlResponse {
        //  Throw error if zipFile is invalid.
        if (!zipFile.exists()) {
            invalidSourceZipError()
        }
        val fileMd5: String = Base64.getEncoder().encodeToString(DigestUtils.md5(FileInputStream(zipFile)))
        val createUploadUrlResponse = createUploadUrl(fileMd5, artifactType, taskType, taskName)
        val url = createUploadUrlResponse.uploadUrl()

        LOG.debug { "Uploading $artifactType using the presigned URL." }

        uploadArtifactToS3(
            url,
            createUploadUrlResponse.uploadId(),
            zipFile,
            fileMd5,
            createUploadUrlResponse.kmsKeyArn(),
            createUploadUrlResponse.requestHeaders()
        )
        return createUploadUrlResponse
    }

    @Throws(IOException::class)
    fun uploadArtifactToS3(
        url: String,
        uploadId: String,
        fileToUpload: File,
        md5: String,
        kmsArn: String?,
        requestHeaders: Map<String, String>?,
    ) {
        try {
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
                val connection = it.connection as HttpURLConnection
                connection.setFixedLengthStreamingMode(fileToUpload.length())
                IoUtils.copy(fileToUpload.inputStream(), connection.outputStream)
            }
        } catch (e: Exception) {
            LOG.debug { "Artifact failed to upload in the S3 bucket: ${e.message}" }
            val errorMessage = getTelemetryErrorMessage(e)
            throw RuntimeException(errorMessage)
        }
    }

    fun createUploadUrl(
        md5Content: String,
        artifactType: String,
        uploadTaskType: CodeWhispererConstants.UploadTaskType,
        taskName: String,
    ): CreateUploadUrlResponse = try {
        CodeWhispererClientAdaptor.getInstance(project).createUploadUrl(
            CreateUploadUrlRequest.builder()
                .contentMd5(md5Content)
                .artifactType(artifactType)
                .uploadIntent(getUploadIntent(uploadTaskType))
                .uploadContext(
                    if (uploadTaskType == CodeWhispererConstants.UploadTaskType.CODE_FIX) {
                        UploadContext.fromCodeFixUploadContext(CodeFixUploadContext.builder().codeFixName(taskName).build())
                    } else {
                        UploadContext.fromCodeAnalysisUploadContext(CodeAnalysisUploadContext.builder().codeScanName(taskName).build())
                    }
                )
                .build()
        )
    } catch (e: Exception) {
        LOG.debug { "Create Upload URL failed: ${e.message}" }
        val errorMessage = getTelemetryErrorMessage(e)
        throw RuntimeException(errorMessage)
    }

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

fun getTelemetryErrorMessage(e: Exception): String = when {
    e.message?.contains("Resource not found.") == true -> "Resource not found."
    e.message?.contains("Maximum com.amazon.aws.codewhisperer.StartCodeAnalysis reached for this month.") == true -> message(
        "testgen.error.maximum_generations_reach"
    )
    e.message?.contains("Service returned HTTP status code 407") == true -> "Service returned HTTP status code 407"
    e.message?.contains("Improperly formed request") == true -> "Improperly formed request"
    e.message?.contains("Service returned HTTP status code 403") == true -> "Service returned HTTP status code 403"
    e.message?.contains("invalid_grant: Invalid token provided") == true -> "invalid_grant: Invalid token provided"
    e.message?.contains("Connect timed out") == true -> "Unable to execute HTTP request: Connect timed out" // Error: Connect to host failed
    e.message?.contains("Encountered an unexpected error when processing the request, please try again.") == true ->
        "Encountered an unexpected error when processing the request, please try again."
    else -> e.message ?: message("codewhisperer.codescan.run_scan_error_telemetry")
}
