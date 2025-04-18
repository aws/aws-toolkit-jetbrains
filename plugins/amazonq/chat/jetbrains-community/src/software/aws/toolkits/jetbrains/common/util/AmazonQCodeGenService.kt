// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.common.util

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.intellij.openapi.project.Project
import software.amazon.awssdk.services.codewhispererruntime.model.CodeWhispererRuntimeException
import software.amazon.awssdk.services.codewhispererruntime.model.CreateUploadUrlResponse
import software.amazon.awssdk.services.codewhispererruntime.model.GetTaskAssistCodeGenerationResponse
import software.amazon.awssdk.services.codewhispererruntime.model.ServiceQuotaExceededException
import software.amazon.awssdk.services.codewhispererruntime.model.StartTaskAssistCodeGenerationResponse
import software.amazon.awssdk.services.codewhispererruntime.model.ThrottlingException
import software.amazon.awssdk.services.codewhispererruntime.model.ValidationException
import software.amazon.awssdk.services.codewhispererstreaming.model.CodeWhispererStreamingException
import software.aws.toolkits.core.utils.debug
import software.aws.toolkits.core.utils.error
import software.aws.toolkits.core.utils.getLogger
import software.aws.toolkits.core.utils.warn
import software.aws.toolkits.jetbrains.common.clients.AmazonQCodeGenerateClient
import software.aws.toolkits.jetbrains.common.session.Intent
import software.aws.toolkits.jetbrains.services.amazonqDoc.session.DocGenerationStreamResult
import software.aws.toolkits.jetbrains.services.amazonqDoc.session.ExportDocTaskAssistResultArchiveStreamResult
import software.aws.toolkits.jetbrains.services.amazonqFeatureDev.ApiException
import software.aws.toolkits.jetbrains.services.amazonqFeatureDev.CodeIterationLimitException
import software.aws.toolkits.jetbrains.services.amazonqFeatureDev.ContentLengthException
import software.aws.toolkits.jetbrains.services.amazonqFeatureDev.ExportParseException
import software.aws.toolkits.jetbrains.services.amazonqFeatureDev.FEATURE_NAME
import software.aws.toolkits.jetbrains.services.amazonqFeatureDev.FeatureDevException
import software.aws.toolkits.jetbrains.services.amazonqFeatureDev.FeatureDevOperation
import software.aws.toolkits.jetbrains.services.amazonqFeatureDev.MonthlyConversationLimitError
import software.aws.toolkits.jetbrains.services.amazonqFeatureDev.ServiceException
import software.aws.toolkits.jetbrains.services.amazonqFeatureDev.ZipFileCorruptedException
import software.aws.toolkits.jetbrains.services.cwc.controller.chat.telemetry.getStartUrl
import software.aws.toolkits.telemetry.AmazonqTelemetry
import software.aws.toolkits.telemetry.MetricResult
import software.aws.toolkits.jetbrains.services.amazonqDoc.ContentLengthException as DocContentLengthException

class AmazonQCodeGenService(val proxyClient: AmazonQCodeGenerateClient, val project: Project) {
    fun createConversation(): String {
        val startTime = System.currentTimeMillis()
        var failureReason: String? = null
        var failureReasonDesc: String? = null
        var result: MetricResult = MetricResult.Succeeded
        var conversationId: String? = null
        try {
            logger.debug { "Executing createTaskAssistConversation" }
            val taskAssistConversationResult = proxyClient.createTaskAssistConversation()
            conversationId = taskAssistConversationResult.conversationId()
            logger.debug {
                "$FEATURE_NAME: Created conversation: {conversationId: $conversationId, requestId: ${
                    taskAssistConversationResult.responseMetadata().requestId()
                }"
            }

            return conversationId
        } catch (e: Exception) {
            logger.warn(e) { "$FEATURE_NAME: Failed to start conversation: ${e.message}" }
            result = MetricResult.Failed
            failureReason = e.javaClass.simpleName
            if (e is FeatureDevException) {
                failureReason = e.reason()
                failureReasonDesc = e.reasonDesc()
            }
            var errMssg = e.message
            if (e is CodeWhispererRuntimeException) {
                errMssg = e.awsErrorDetails().errorMessage()
                logger.warn(e) { "Start conversation failed for request: ${e.requestId()}" }

                if (e is ServiceQuotaExceededException) {
                    throw MonthlyConversationLimitError(errMssg, operation = FeatureDevOperation.CreateConversation.toString(), desc = null, cause = e.cause)
                }
                throw ApiException.of(e.statusCode(), errMssg, operation = FeatureDevOperation.CreateConversation.toString(), desc = null, e.cause)
            }
            throw ServiceException(
                errMssg ?: "CreateTaskAssistConversation failed",
                operation = FeatureDevOperation.CreateConversation.toString(),
                desc = null,
                e.cause
            )
        } finally {
            AmazonqTelemetry.startConversationInvoke(
                amazonqConversationId = conversationId,
                result = result,
                reason = failureReason,
                reasonDesc = failureReasonDesc,
                duration = (System.currentTimeMillis() - startTime).toDouble(),
                credentialStartUrl = getStartUrl(project = this.project),
            )
        }
    }

    fun createUploadUrl(conversationId: String, contentChecksumSha256: String, contentLength: Long, uploadId: String, featureName: String? = null):
        CreateUploadUrlResponse {
        try {
            logger.debug { "Executing createUploadUrl with conversationId $conversationId" }
            val uploadUrlResponse = proxyClient.createTaskAssistUploadUrl(
                conversationId,
                contentChecksumSha256,
                contentLength,
            )
            logger.debug {
                "$FEATURE_NAME: Created upload url: {uploadId: $uploadId, requestId: ${uploadUrlResponse.responseMetadata().requestId()}}"
            }
            return uploadUrlResponse
        } catch (e: Exception) {
            logger.warn(e) { "$FEATURE_NAME: Failed to generate presigned url: ${e.message}" }

            var errMssg = e.message
            if (e is CodeWhispererRuntimeException) {
                errMssg = e.awsErrorDetails().errorMessage()
                logger.warn(e) { "Create UploadUrl failed for request: ${e.requestId()}" }

                if (e is ValidationException && e.message?.contains("Invalid contentLength") == true) {
                    if (featureName?.equals("docGeneration") == true) {
                        throw DocContentLengthException(operation = FeatureDevOperation.CreateUploadUrl.toString(), desc = null, cause = e.cause)
                    }
                    throw ContentLengthException(operation = FeatureDevOperation.CreateUploadUrl.toString(), desc = null, cause = e.cause)
                }

                throw ApiException.of(e.statusCode(), errMssg, operation = FeatureDevOperation.CreateUploadUrl.toString(), desc = null, e.cause)
            }
            throw ServiceException(errMssg ?: "CreateUploadUrl failed", operation = FeatureDevOperation.CreateUploadUrl.toString(), desc = null, e.cause)
        }
    }

    open fun startTaskAssistCodeGeneration(conversationId: String, uploadId: String, message: String, intent: Intent):
        StartTaskAssistCodeGenerationResponse {
        try {
            logger.debug { "Executing startTaskAssistCodeGeneration with conversationId: $conversationId , uploadId: $uploadId" }
            val startCodeGenerationResponse = proxyClient.startTaskAssistCodeGeneration(
                conversationId,
                uploadId,
                message,
                intent
            )

            logger.debug { "$FEATURE_NAME: Started code generation with requestId: ${startCodeGenerationResponse.responseMetadata().requestId()}" }
            return startCodeGenerationResponse
        } catch (e: Exception) {
            logger.warn(e) { "$FEATURE_NAME: Failed to execute startTaskAssistCodeGeneration ${e.message}" }

            var errMssg = e.message
            if (e is CodeWhispererRuntimeException) {
                errMssg = e.awsErrorDetails().errorMessage()
                logger.warn(e) { "StartTaskAssistCodeGeneration failed for request: ${e.requestId()}" }

                // API Front-end will throw Throttling if conversation limit is reached. API Front-end monitors StartCodeGeneration for throttling
                if (e is software.amazon.awssdk.services.codewhispererruntime.model.ThrottlingException &&
                    e.message?.contains("StartTaskAssistCodeGeneration reached for this month.") == true
                ) {
                    throw MonthlyConversationLimitError(errMssg, operation = FeatureDevOperation.StartTaskAssistCodeGeneration.toString(), desc = null, e.cause)
                }

                if (e is ServiceQuotaExceededException || (
                        e is ThrottlingException && e.message?.contains(
                            "limit for number of iterations on a code generation"
                        ) == true
                        )
                ) {
                    throw CodeIterationLimitException(operation = FeatureDevOperation.StartTaskAssistCodeGeneration.toString(), desc = null, e.cause)
                } else if (e is ValidationException && e.message?.contains("repo size is exceeding the limits") == true) {
                    if (intent == Intent.DOC) {
                        throw DocContentLengthException(operation = FeatureDevOperation.CreateUploadUrl.toString(), desc = null, cause = e.cause)
                    }
                    throw ContentLengthException(operation = FeatureDevOperation.StartTaskAssistCodeGeneration.toString(), desc = null, cause = e.cause)
                } else if (e is ValidationException && e.message?.contains("zipped file is corrupted") == true) {
                    throw ZipFileCorruptedException(operation = FeatureDevOperation.StartTaskAssistCodeGeneration.toString(), desc = null, e.cause)
                }
                throw ApiException.of(e.statusCode(), errMssg, operation = FeatureDevOperation.StartTaskAssistCodeGeneration.toString(), desc = null, e.cause)
            }
            throw ServiceException(
                errMssg ?: "StartTaskAssistCodeGeneration failed",
                operation = FeatureDevOperation.StartTaskAssistCodeGeneration.toString(),
                desc = null,
                e.cause
            )
        }
    }

    fun getTaskAssistCodeGeneration(conversationId: String, codeGenerationId: String): GetTaskAssistCodeGenerationResponse {
        try {
            logger.debug { "Executing GetTaskAssistCodeGeneration with conversationId: $conversationId , codeGenerationId: $codeGenerationId" }
            val getCodeGenerationResponse = proxyClient.getTaskAssistCodeGeneration(conversationId, codeGenerationId)

            logger.debug {
                "$FEATURE_NAME: Received code generation status $getCodeGenerationResponse with requestId ${
                    getCodeGenerationResponse.responseMetadata()
                        .requestId()
                }"
            }
            return getCodeGenerationResponse
        } catch (e: Exception) {
            logger.warn(e) { "$FEATURE_NAME: Failed to execute GetTaskAssistCodeGeneration ${e.message}" }

            var errMssg = e.message
            if (e is CodeWhispererRuntimeException) {
                errMssg = e.awsErrorDetails().errorMessage()
                logger.warn(e) { "GetTaskAssistCodeGeneration failed for request:  ${e.requestId()}" }
                throw ApiException.of(e.statusCode(), errMssg, operation = FeatureDevOperation.GetTaskAssistCodeGeneration.toString(), desc = null, e.cause)
            }
            throw ServiceException(
                errMssg ?: "GetTaskAssistCodeGeneration failed",
                operation = FeatureDevOperation.GetTaskAssistCodeGeneration.toString(),
                desc = null,
                e.cause
            )
        }
    }

    suspend fun exportTaskAssistArchiveResult(conversationId: String): DocGenerationStreamResult {
        val exportResponse: MutableList<ByteArray>
        try {
            exportResponse = proxyClient.exportTaskAssistResultArchive(conversationId)
            logger.debug { "$FEATURE_NAME: Received export task assist result archive response" }
        } catch (e: Exception) {
            logger.warn(e) { "$FEATURE_NAME: Failed to export archive result: ${e.message}" }

            var errMssg = e.message
            if (e is CodeWhispererStreamingException) {
                errMssg = e.awsErrorDetails().errorMessage()
                logger.warn(e) { "ExportTaskAssistArchiveResult failed for request: ${e.requestId()}" }
            }
            throw ServiceException(
                errMssg ?: "ExportTaskAssistArchive failed",
                operation = FeatureDevOperation.ExportTaskAssistArchiveResult.toString(),
                desc = null,
                e.cause
            )
        }

        val parsedResult: ExportDocTaskAssistResultArchiveStreamResult
        try {
            val result = exportResponse.reduce { acc, next -> acc + next } // To map the result it is needed to combine the  full byte array
            parsedResult = jacksonObjectMapper().readValue(result)
        } catch (e: Exception) {
            logger.error(e) { "Failed to parse downloaded code results" }
            throw ExportParseException(operation = FeatureDevOperation.ExportTaskAssistArchiveResult.toString(), desc = null, e.cause)
        }

        return parsedResult.codeGenerationResult
    }

    companion object {
        private val logger = getLogger<AmazonQCodeGenerateClient>()
    }
}
