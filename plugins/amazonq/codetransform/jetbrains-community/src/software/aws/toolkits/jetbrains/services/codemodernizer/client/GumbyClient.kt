// Copyright 2023 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.codemodernizer.client

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.util.io.HttpRequests
import software.amazon.awssdk.services.codewhispererruntime.CodeWhispererRuntimeClient
import software.amazon.awssdk.services.codewhispererruntime.model.CodeWhispererRuntimeResponse
import software.amazon.awssdk.services.codewhispererruntime.model.ContentChecksumType
import software.amazon.awssdk.services.codewhispererruntime.model.CreateUploadUrlRequest
import software.amazon.awssdk.services.codewhispererruntime.model.CreateUploadUrlResponse
import software.amazon.awssdk.services.codewhispererruntime.model.GetTransformationPlanRequest
import software.amazon.awssdk.services.codewhispererruntime.model.GetTransformationPlanResponse
import software.amazon.awssdk.services.codewhispererruntime.model.GetTransformationRequest
import software.amazon.awssdk.services.codewhispererruntime.model.GetTransformationResponse
import software.amazon.awssdk.services.codewhispererruntime.model.ResumeTransformationRequest
import software.amazon.awssdk.services.codewhispererruntime.model.ResumeTransformationResponse
import software.amazon.awssdk.services.codewhispererruntime.model.StartTransformationRequest
import software.amazon.awssdk.services.codewhispererruntime.model.StartTransformationResponse
import software.amazon.awssdk.services.codewhispererruntime.model.StopTransformationRequest
import software.amazon.awssdk.services.codewhispererruntime.model.StopTransformationResponse
import software.amazon.awssdk.services.codewhispererruntime.model.TransformationDownloadArtifact
import software.amazon.awssdk.services.codewhispererruntime.model.TransformationLanguage
import software.amazon.awssdk.services.codewhispererruntime.model.TransformationPlan
import software.amazon.awssdk.services.codewhispererruntime.model.TransformationProgressUpdate
import software.amazon.awssdk.services.codewhispererruntime.model.TransformationStep
import software.amazon.awssdk.services.codewhispererruntime.model.TransformationType
import software.amazon.awssdk.services.codewhispererruntime.model.TransformationUserActionStatus
import software.amazon.awssdk.services.codewhispererruntime.model.UploadIntent
import software.amazon.awssdk.services.codewhispererstreaming.model.ExportIntent
import software.aws.toolkits.core.utils.error
import software.aws.toolkits.core.utils.getLogger
import software.aws.toolkits.jetbrains.core.AwsClientManager
import software.aws.toolkits.jetbrains.core.awsClient
import software.aws.toolkits.jetbrains.core.credentials.ToolkitConnectionManager
import software.aws.toolkits.jetbrains.core.credentials.pinning.QConnection
import software.aws.toolkits.jetbrains.services.amazonq.APPLICATION_ZIP
import software.aws.toolkits.jetbrains.services.amazonq.AWS_KMS
import software.aws.toolkits.jetbrains.services.amazonq.CONTENT_SHA256
import software.aws.toolkits.jetbrains.services.amazonq.SERVER_SIDE_ENCRYPTION
import software.aws.toolkits.jetbrains.services.amazonq.SERVER_SIDE_ENCRYPTION_AWS_KMS_KEY_ID
import software.aws.toolkits.jetbrains.services.amazonq.clients.AmazonQStreamingClient
import software.aws.toolkits.jetbrains.services.codemodernizer.CodeTransformTelemetryManager
import software.aws.toolkits.jetbrains.services.codemodernizer.model.JobId
import software.aws.toolkits.telemetry.CodeTransformApiNames
import java.io.File
import java.net.HttpURLConnection
import java.time.Instant

@Service(Service.Level.PROJECT)
class GumbyClient(private val project: Project) {
    private val telemetry = CodeTransformTelemetryManager.getInstance(project)
    private fun connection() = ToolkitConnectionManager.getInstance(project).activeConnectionForFeature(QConnection.getInstance())
        ?: error("Attempted to use connection while one does not exist")

    private fun bearerClient() = connection().getConnectionSettings().awsClient<CodeWhispererRuntimeClient>()

    private val amazonQStreamingClient
        get() = AmazonQStreamingClient.getInstance(project)

    fun createGumbyUploadUrl(sha256Checksum: String): CreateUploadUrlResponse {
        val request = CreateUploadUrlRequest.builder()
            .contentChecksumType(ContentChecksumType.SHA_256)
            .contentChecksum(sha256Checksum)
            .uploadIntent(UploadIntent.TRANSFORMATION)
            .build()
        return callApi({ bearerClient().createUploadUrl(request) }, apiName = CodeTransformApiNames.CreateUploadUrl)
    }

    fun getCodeModernizationJob(jobId: String): GetTransformationResponse {
        val request = GetTransformationRequest.builder().transformationJobId(jobId).build()
        return callApi({ bearerClient().getTransformation(request) }, apiName = CodeTransformApiNames.GetTransformation, jobId = jobId)
    }

    fun startCodeModernization(
        uploadId: String,
        sourceLanguage: TransformationLanguage,
        targetLanguage: TransformationLanguage
    ): StartTransformationResponse {
        val request = StartTransformationRequest.builder()
            .workspaceState { state ->
                state.programmingLanguage { it.languageName("java") }
                    .uploadId(uploadId)
            }
            .transformationSpec { spec ->
                spec.transformationType(TransformationType.LANGUAGE_UPGRADE)
                    .source { it.language(sourceLanguage) }
                    .target { it.language(targetLanguage) }
            }
            .build()
        return callApi({ bearerClient().startTransformation(request) }, apiName = CodeTransformApiNames.StartTransformation, uploadId = uploadId)
    }

    fun resumeCodeTransformation(
        jobId: String,
        userActionStatus: TransformationUserActionStatus
    ): ResumeTransformationResponse {
        val request = ResumeTransformationRequest.builder()
            .transformationJobId(jobId)
            .userActionStatus(userActionStatus)
            .build()
        return callApi({ bearerClient().resumeTransformation(request) }, apiName = CodeTransformApiNames.ResumeTransformation, jobId = jobId)
    }

    fun getCodeModernizationPlan(jobId: JobId): GetTransformationPlanResponse {
        val request = GetTransformationPlanRequest.builder().transformationJobId(jobId.id).build()
        return callApi({ bearerClient().getTransformationPlan(request) }, apiName = CodeTransformApiNames.GetTransformationPlan, jobId = jobId.id)
    }
    // TODO remove
    fun getCodeModernizationPlanMock(jobId: JobId, count: Int): GetTransformationPlanResponse {
        val plan1 = TransformationPlan.builder().transformationSteps(
            listOf(
                TransformationStep.builder()
                    .id("1")
                    .name("Step 1 - Update dependencies and code")
                    .description("Q will update mandatory package dependencies and frameworks. Also, where required for compatability with Java 17, it will replace deprecated code with working code.")
                    .status("CREATED")
                    .progressUpdates(listOf())
                    .build(),
                TransformationStep.builder()
                    .id("2")
                    .name("Step 2 - Build in Java 17 and fix any issues")
                    .description("Q will build the upgraded code in Java 17 and iteratively fix any build errors encountered.")
                    .status("CREATED")
                    .progressUpdates(listOf())
                    .build(),
                TransformationStep.builder()
                    .id("3")
                    .name("Step 3 - Finalize code changes and generate transformation summary")
                    .description("Q will generate code changes for you to review and accept. It will also summarize the changes made, and will copy over build logs for future reference and troubleshooting.")
                    .status("CREATED")
                    .progressUpdates(listOf())
                    .build(),
            )
        ).build()

        val plan2 = TransformationPlan.builder().transformationSteps(
            listOf(
                TransformationStep.builder()
                    .id("1")
                    .name("Step 1 - Update dependencies and code")
                    .description("Q will update mandatory package dependencies and frameworks. Also, where required for compatability with Java 17, it will replace deprecated code with working code.")
                    .status("CREATED")
                    .progressUpdates(
                        TransformationProgressUpdate
                            .builder()
                            .name("Applying dependencies and code changes")
                            .status("IN_PROGRESS")
                            .description("Step started")
                            .startTime(Instant.parse("2024-04-16T04:26:51.471Z"))
                            .build()
                    )
                    .startTime(Instant.parse("2024-04-16T04:26:51.471Z"))
                    .build(),
                TransformationStep.builder()
                    .id("2")
                    .name("Step 2 - Build in Java 17 and fix any issues")
                    .description("Q will build the upgraded code in Java 17 and iteratively fix any build errors encountered.")
                    .status("CREATED")
                    .progressUpdates(listOf())
                    .build(),
                TransformationStep.builder()
                    .id("3")
                    .name("Step 3 - Finalize code changes and generate transformation summary")
                    .description("Q will generate code changes for you to review and accept. It will also summarize the changes made, and will copy over build logs for future reference and troubleshooting.")
                    .status("CREATED")
                    .progressUpdates(listOf())
                    .build(),
            )
        ).build()

        val plan3 = TransformationPlan.builder().transformationSteps(
            listOf(
                TransformationStep.builder()
                    .id("1")
                    .name("Step 1 - Update dependencies and code")
                    .description("Q will update mandatory package dependencies and frameworks. Also, where required for compatability with Java 17, it will replace deprecated code with working code.")
                    .status("CREATED")
                    .progressUpdates(
                        TransformationProgressUpdate
                            .builder()
                            .name("Applying dependencies and code changes")
                            .status("COMPLETED")
                            .description("Step finished successfully")
                            .startTime(Instant.parse("2024-04-16T04:26:51.471Z"))
                            .endTime(Instant.parse("2024-04-16T04:27:23.054Z"))
                            .build(),
                        TransformationProgressUpdate
                            .builder()
                            .name("Building in Java 17 environment")
                            .status("IN_PROGRESS")
                            .description("Migration step started")
                            .startTime(Instant.parse("2024-04-16T04:27:23.223Z"))
                            .build()
                    )
                    .startTime(Instant.parse("2024-04-16T04:26:51.471Z"))
                    .build(),
                TransformationStep.builder()
                    .id("2")
                    .name("Step 2 - Build in Java 17 and fix any issues")
                    .description("Q will build the upgraded code in Java 17 and iteratively fix any build errors encountered.")
                    .status("CREATED")
                    .progressUpdates(listOf())
                    .build(),
                TransformationStep.builder()
                    .id("3")
                    .name("Step 3 - Finalize code changes and generate transformation summary")
                    .description("Q will generate code changes for you to review and accept. It will also summarize the changes made, and will copy over build logs for future reference and troubleshooting.")
                    .status("CREATED")
                    .progressUpdates(listOf())
                    .build(),
            )
        ).build()

        val plan4 = TransformationPlan.builder().transformationSteps(
            listOf(
                TransformationStep.builder()
                    .id("1")
                    .name("Step 1 - Update dependencies and code")
                    .description("Q will update mandatory package dependencies and frameworks. Also, where required for compatability with Java 17, it will replace deprecated code with working code.")
                    .status("CREATED")
                    .progressUpdates(
                        TransformationProgressUpdate
                            .builder()
                            .name("Applying dependencies and code changes")
                            .status("COMPLETED")
                            .description("Step finished successfully")
                            .startTime(Instant.parse("2024-04-16T04:26:51.471Z"))
                            .endTime(Instant.parse("2024-04-16T04:27:23.054Z"))
                            .build(),
                        TransformationProgressUpdate
                            .builder()
                            .name("Building in Java 17 environment")
                            .status("PAUSED")
                            .description("Compile Failed. Error encountered for dependency incompatibility. Paused to get user input.")
                            .startTime(Instant.parse("2024-04-16T04:27:23.223Z"))
                            .endTime(Instant.parse("2024-04-16T04:29:53.836Z"))
                            .downloadArtifacts(listOf(
                                TransformationDownloadArtifact
                                    .builder()
                                    .downloadArtifactType("CLIENT_INSTRUCTIONS")
                                    .downloadArtifactId("someID")
                                    .build()
                            ))
                            .build()
                    )
                    .startTime(Instant.parse("2024-04-16T04:26:51.471Z"))
                    .build(),
                TransformationStep.builder()
                    .id("2")
                    .name("Step 2 - Build in Java 17 and fix any issues")
                    .description("Q will build the upgraded code in Java 17 and iteratively fix any build errors encountered.")
                    .status("CREATED")
                    .progressUpdates(listOf())
                    .build(),
                TransformationStep.builder()
                    .id("3")
                    .name("Step 3 - Finalize code changes and generate transformation summary")
                    .description("Q will generate code changes for you to review and accept. It will also summarize the changes made, and will copy over build logs for future reference and troubleshooting.")
                    .status("CREATED")
                    .progressUpdates(listOf())
                    .build(),
            )
        ).build()

        val plans = listOf(plan1, plan2, plan3, plan4)

        return GetTransformationPlanResponse.builder().transformationPlan(plans[count]).build()
    }



    fun stopTransformation(transformationJobId: String): StopTransformationResponse {
        val request = StopTransformationRequest.builder().transformationJobId(transformationJobId).build()
        return callApi({ bearerClient().stopTransformation(request) }, apiName = CodeTransformApiNames.StopTransformation, jobId = transformationJobId)
    }

    private fun <T : CodeWhispererRuntimeResponse> callApi(
        apiCall: () -> T,
        apiName: CodeTransformApiNames,
        jobId: String? = null,
        uploadId: String? = null,
    ): T {
        val startTime = Instant.now()
        var result: CodeWhispererRuntimeResponse? = null
        try {
            result = apiCall()
            return result
        } catch (e: Exception) {
            LOG.error(e) { "$apiName failed: ${e.message}" }
            telemetry.apiError(e.message.toString(), apiName, jobId)
            throw e // pass along error to callee
        } finally {
            telemetry.logApiLatency(
                apiName,
                startTime,
                codeTransformUploadId = uploadId,
                codeTransformJobId = jobId,
                codeTransformRequestId = result?.responseMetadata()?.requestId(),
            )
        }
    }

    // TODO look here for download
    suspend fun downloadExportResultArchive(jobId: JobId): MutableList<ByteArray> = amazonQStreamingClient.exportResultArchive(
        jobId.id,
        ExportIntent.TRANSFORMATION,
        { e ->
            LOG.error(e) { "${CodeTransformApiNames.ExportResultArchive} failed: ${e.message}" }
            telemetry.apiError(e.localizedMessage, CodeTransformApiNames.ExportResultArchive, jobId.id)
        },
        { startTime ->
            // TODO need to update telemetry type to include export ID
            telemetry.logApiLatency(CodeTransformApiNames.ExportResultArchive, startTime, codeTransformJobId = jobId.id)
        }
    )

    // TODO update telemetry
    suspend fun downloadExportResultArchive2(exportId: String): MutableList<ByteArray> = amazonQStreamingClient.exportResultArchive(
        exportId,
        ExportIntent.TRANSFORMATION,
        { e ->
            LOG.error(e) { "${CodeTransformApiNames.ExportResultArchive} failed: ${e.message}" }
            telemetry.apiError(e.localizedMessage, CodeTransformApiNames.ExportResultArchive, exportId)
        },
        { startTime ->
            // TODO need to update telemetry type to include export ID
            telemetry.logApiLatency(CodeTransformApiNames.ExportResultArchive, startTime, codeTransformJobId = "")
        }
    )

    /*
     * Adapted from [CodeWhispererCodeScanSession]
     */
    fun uploadArtifactToS3(url: String, fileToUpload: File, checksum: String, kmsArn: String, shouldStop: () -> Boolean) {
        HttpRequests.put(url, APPLICATION_ZIP).userAgent(AwsClientManager.userAgent).tuner {
            it.setRequestProperty(CONTENT_SHA256, checksum)
            if (kmsArn.isNotEmpty()) {
                it.setRequestProperty(SERVER_SIDE_ENCRYPTION, AWS_KMS)
                it.setRequestProperty(SERVER_SIDE_ENCRYPTION_AWS_KMS_KEY_ID, kmsArn)
            }
        }
            .connect { request -> // default connect timeout is 10s
                val connection = request.connection as HttpURLConnection
                connection.setFixedLengthStreamingMode(fileToUpload.length())
                fileToUpload.inputStream().use { inputStream ->
                    connection.outputStream.use {
                        val bufferSize = 4096
                        val array = ByteArray(bufferSize)
                        var n = inputStream.readNBytes(array, 0, bufferSize)
                        while (0 != n) {
                            if (shouldStop()) break
                            it.write(array, 0, n)
                            n = inputStream.readNBytes(array, 0, bufferSize)
                        }
                    }
                }
            }
    }

    companion object {
        private val LOG = getLogger<GumbyClient>()

        fun getInstance(project: Project) = project.service<GumbyClient>()
    }
}
