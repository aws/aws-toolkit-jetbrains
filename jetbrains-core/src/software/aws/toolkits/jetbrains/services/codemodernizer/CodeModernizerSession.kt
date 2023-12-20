// Copyright 2023 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.codemodernizer

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.runInEdt
import com.intellij.serviceContainer.AlreadyDisposedException
import com.intellij.util.io.HttpRequests
import kotlinx.coroutines.delay
import org.apache.commons.codec.digest.DigestUtils
import software.amazon.awssdk.services.codewhispererruntime.model.StartTransformationResponse
import software.amazon.awssdk.services.codewhispererruntime.model.TransformationJob
import software.amazon.awssdk.services.codewhispererruntime.model.TransformationLanguage
import software.amazon.awssdk.services.codewhispererruntime.model.TransformationPlan
import software.amazon.awssdk.services.codewhispererruntime.model.TransformationStatus
import software.aws.toolkits.core.utils.error
import software.aws.toolkits.core.utils.getLogger
import software.aws.toolkits.core.utils.warn
import software.aws.toolkits.jetbrains.core.AwsClientManager
import software.aws.toolkits.jetbrains.core.explorer.refreshCwQTree
import software.aws.toolkits.jetbrains.services.codemodernizer.client.GumbyClient
import software.aws.toolkits.jetbrains.services.codemodernizer.model.AwaitModernizationPlanResult
import software.aws.toolkits.jetbrains.services.codemodernizer.model.CodeModernizerException
import software.aws.toolkits.jetbrains.services.codemodernizer.model.CodeModernizerJobCompletedResult
import software.aws.toolkits.jetbrains.services.codemodernizer.model.CodeModernizerSessionContext
import software.aws.toolkits.jetbrains.services.codemodernizer.model.CodeModernizerStartJobResult
import software.aws.toolkits.jetbrains.services.codemodernizer.model.JobId
import software.aws.toolkits.jetbrains.services.codemodernizer.model.ZipCreationResult
import software.aws.toolkits.jetbrains.services.codemodernizer.plan.CodeModernizerPlanEditorProvider
import software.aws.toolkits.jetbrains.services.codemodernizer.state.CodeModernizerSessionState
import software.aws.toolkits.jetbrains.services.codemodernizer.state.CodeTransformTelemetryState
import software.aws.toolkits.jetbrains.services.codewhisperer.codescan.CodeWhispererCodeScanSession
import software.aws.toolkits.jetbrains.utils.notifyStickyInfo
import software.aws.toolkits.resources.message
import software.aws.toolkits.telemetry.CodeTransformApiNames
import software.aws.toolkits.telemetry.CodetransformTelemetry
import java.io.File
import java.io.FileInputStream
import java.net.HttpURLConnection
import java.time.Instant
import java.util.Base64
import java.util.concurrent.CancellationException
import java.util.concurrent.atomic.AtomicBoolean

const val ZIP_SOURCES_PATH = "sources"
const val UPLOAD_ZIP_MANIFEST_VERSION = 1.0F

class CodeModernizerSession(
    val sessionContext: CodeModernizerSessionContext,
    val initialPollingSleepDurationMillis: Long = 2000,
    val totalPollingSleepDurationMillis: Long = 5000,
) : Disposable {
    private val clientAdaptor = GumbyClient.getInstance(sessionContext.project)
    private val state = CodeModernizerSessionState.getInstance(sessionContext.project)
    private val isDisposed = AtomicBoolean(false)
    private val shouldStop = AtomicBoolean(false)

    /**
     * Note that this function makes network calls and needs to be run from a background thread.
     * Runs a code modernizer session which comprises the following steps:
     *  1. Generate zip file with the files in the selected module
     *  2. CreateUploadURL to upload the zip.
     *  3. Upload the zip files using the URL.
     *  4. Call startMigrationJob to start a migration job
     *
     *  Based on [CodeWhispererCodeScanSession]
     */
    fun createModernizationJob(): CodeModernizerStartJobResult {
        LOG.warn { "In Create Modernization Job" }
        val payload: File?

        try {
            if (isDisposed.get()) {
                LOG.warn { "Disposed when about to create zip to upload" }
                return CodeModernizerStartJobResult.Disposed
            }
            val startTime = Instant.now()
            val result = sessionContext.createZipWithModuleFiles()
            payload = when (result) {
                is ZipCreationResult.Missing1P -> {
                    notifyStickyInfo(
                        message("codemodernizer.notification.info.maven_failed.title"),
                        message("codemodernizer.notification.info.maven_failed.content")
                    )
                    result.payload
                }

                is ZipCreationResult.Succeeded -> result.payload
            }
            CodetransformTelemetry.jobCreateZipEndTime(
                codeTransformTotalByteSize = payload.length().toInt(),
                codeTransformSessionId = CodeTransformTelemetryState.instance.getSessionId(),
                codeTransformRunTimeLatency = calculateTotalLatency(startTime, Instant.now())
            )
        } catch (e: Exception) {
            LOG.error(e) { e.message.toString() }
            CodetransformTelemetry.logGeneralError(
                codeTransformApiErrorMessage = e.message.toString(),
                codeTransformSessionId = CodeTransformTelemetryState.instance.getSessionId(),
            )
            state.currentJobStatus = TransformationStatus.FAILED
            return when (e) {
                is CodeModernizerException -> CodeModernizerStartJobResult.ZipCreationFailed(e.message)
                else -> CodeModernizerStartJobResult.ZipCreationFailed(message("codemodernizer.notification.warn.zip_creation_failed.reasons.unknown"))
            }
        }

        return try {
            if (shouldStop.get()) {
                LOG.warn { "Job was cancelled by user before upload was called" }
                return CodeModernizerStartJobResult.Cancelled
            }
            val uploadId = uploadPayload(payload)
            if (shouldStop.get()) {
                LOG.warn { "Job was cancelled by user before start job was called" }
                return CodeModernizerStartJobResult.Cancelled
            }
            val startJobResponse = startJob(uploadId)
            state.putJobHistory(sessionContext, "STARTED")
            state.currentJobStatus = TransformationStatus.STARTED
            CodeModernizerStartJobResult.Started(JobId(startJobResponse.transformationJobId()))
        } catch (e: AlreadyDisposedException) {
            LOG.warn { e.localizedMessage }
            return CodeModernizerStartJobResult.Disposed
        } catch (e: Exception) {
            LOG.warn { e.message.toString() }
            state.putJobHistory(sessionContext, "FAILED TO START")
            state.currentJobStatus = TransformationStatus.FAILED
            CodetransformTelemetry.logGeneralError(
                codeTransformApiErrorMessage = e.message.toString(),
                codeTransformSessionId = CodeTransformTelemetryState.instance.getSessionId(),
            )
            CodeModernizerStartJobResult.UnableToStartJob(e.message.toString())
        } finally {
            deleteUploadArtifact(payload)
        }
    }

    internal fun deleteUploadArtifact(payload: File) {
        if (!payload.delete()) {
            LOG.warn { "Unable to delete upload artifact." }
        }
    }

    private suspend fun awaitModernizationPlan(
        jobId: JobId,
        jobTransitionHandler: (currentStatus: TransformationStatus, migrationPlan: TransformationPlan?) -> Unit,
    ): AwaitModernizationPlanResult {
        var passedBuild = false
        var passedStart = false
        val result = jobId.pollTransformationStatusAndPlan(
            succeedOn = STATES_WHERE_PLAN_EXIST,
            failOn = STATES_WHERE_JOB_STOPPED_PRE_PLAN_READY,
            clientAdaptor,
            initialPollingSleepDurationMillis,
            totalPollingSleepDurationMillis,
            isDisposed,
            sessionContext.project,
        ) { old, new, plan ->
            LOG.warn { "in awaitModernizationPlan, state changed for job $jobId: $old -> $new" }
            state.currentJobStatus = new
            sessionContext.project.refreshCwQTree()
            val instant = Instant.now()
            state.updateJobHistory(sessionContext, new.name, instant)
            setCurrentJobStopTime(new, instant)
            setCurrentJobSummary(new)
            jobTransitionHandler(new, plan)
            if (!passedStart && new in STATES_AFTER_STARTED) {
                passedStart = true
            }
            if (!passedBuild && new in STATES_AFTER_INITIAL_BUILD) {
                passedBuild = true
            }
        }
        return when {
            result.succeeded && result.transformationPlan != null -> AwaitModernizationPlanResult.Success(result.transformationPlan)
            result.state == TransformationStatus.UNKNOWN_TO_SDK_VERSION -> AwaitModernizationPlanResult.UnknownStatusWhenPolling
            !passedStart && result.state == TransformationStatus.FAILED -> AwaitModernizationPlanResult.Failure(
                result.jobDetails?.reason() ?: message("codemodernizer.notification.warn.unknown_start_failure")
            )

            !passedBuild && result.state == TransformationStatus.FAILED -> AwaitModernizationPlanResult.BuildFailed(
                result.jobDetails?.reason() ?: message("codemodernizer.notification.warn.unknown_build_failure")
            )

            else -> AwaitModernizationPlanResult.Failure(message("codemodernizer.notification.warn.unknown_status_response"))
        }
    }

    private fun startJob(uploadId: String): StartTransformationResponse {
        val sourceLanguage = sessionContext.sourceJavaVersion.name.toTransformationLanguage()
        val targetLanguage = sessionContext.targetJavaVersion.name.toTransformationLanguage()
        if (sourceLanguage == TransformationLanguage.UNKNOWN_TO_SDK_VERSION) {
            throw RuntimeException("Source language is not supported")
        }
        if (targetLanguage == TransformationLanguage.UNKNOWN_TO_SDK_VERSION) {
            throw RuntimeException("Target language is not supported")
        }
        LOG.warn { "Starting job with uploadId $uploadId for $sourceLanguage -> $targetLanguage" }
        val apiStartTime = Instant.now()
        try {
            val startTransformResult = clientAdaptor.startCodeModernization(uploadId, sourceLanguage, targetLanguage)
            LOG.warn { "Started job with uploadId $uploadId" }
            CodetransformTelemetry.logApiLatency(
                codeTransformApiNames = CodeTransformApiNames.StartTransformation,
                codeTransformSessionId = CodeTransformTelemetryState.instance.getSessionId(),
                codeTransformRunTimeLatency = calculateTotalLatency(apiStartTime, Instant.now()),
                codeTransformUploadId = startTransformResult.transformationJobId(),
                codeTransformRequestId = startTransformResult.responseMetadata().requestId()
            )
            return startTransformResult
        } catch (e: Exception) {
            CodetransformTelemetry.logApiError(
                codeTransformApiNames = CodeTransformApiNames.StartTransformation,
                codeTransformSessionId = CodeTransformTelemetryState.instance.getSessionId(),
                codeTransformApiErrorMessage = e.message.toString(),
            )
            throw e // pass along error to callee
        }
    }

    /**
     * Will perform a single call, checking if a modernization job is finished.
     */
    fun getJobDetails(jobId: JobId): TransformationJob {
        LOG.warn { "In isJobFinished " }
        val apiStartTime = Instant.now()
        val transformationResponse = try {
            clientAdaptor.getCodeModernizationJob(jobId.id)
        } catch (e: Exception) {
            CodetransformTelemetry.logApiError(
                codeTransformApiErrorMessage = e.message.toString(),
                codeTransformApiNames = CodeTransformApiNames.GetTransformation,
                codeTransformSessionId = CodeTransformTelemetryState.instance.getSessionId(),
                codeTransformJobId = jobId.id,
            )
            throw e // pass along error to callee
        }

        CodetransformTelemetry.logApiLatency(
            codeTransformApiNames = CodeTransformApiNames.GetTransformation,
            codeTransformSessionId = CodeTransformTelemetryState.instance.getSessionId(),
            codeTransformRunTimeLatency = calculateTotalLatency(apiStartTime, Instant.now()),
            codeTransformJobId = jobId.id,
            codeTransformRequestId = transformationResponse.responseMetadata().requestId(),
        )
        return transformationResponse.transformationJob()
    }

    /**
     * This will resume the job, i.e. it will resume the main job loop kicked of by [createModernizationJob]
     */
    fun resumeJob(startTime: Instant) = state.putJobHistory(sessionContext, "Started", startTime)

    /*
     * Adapted from [CodeWhispererCodeScanSession]
     */
    fun uploadArtifactToS3(url: String, fileToUpload: File, checksum: String, kmsArn: String) {
        HttpRequests.put(url, APPLICATION_ZIP).userAgent(AwsClientManager.userAgent).tuner {
            it.setRequestProperty(CONTENT_SHA256, checksum)
            if (kmsArn.isNotEmpty()) {
                it.setRequestProperty(CodeWhispererCodeScanSession.SERVER_SIDE_ENCRYPTION, CodeWhispererCodeScanSession.AWS_KMS)
                it.setRequestProperty(CodeWhispererCodeScanSession.SERVER_SIDE_ENCRYPTION_AWS_KMS_KEY_ID, kmsArn)
            }
        }
            .connect { request ->
                val connection = request.connection as HttpURLConnection
                connection.setFixedLengthStreamingMode(fileToUpload.length())
                fileToUpload.inputStream().use { inputStream ->
                    connection.outputStream.use {
                        val bufferSize = 4096
                        val array = ByteArray(bufferSize)
                        var n = inputStream.readNBytes(array, 0, bufferSize)
                        while (0 != n) {
                            if (shouldStop.get()) break
                            it.write(array, 0, n)
                            n = inputStream.readNBytes(array, 0, bufferSize)
                        }
                    }
                }
            }
    }

    /**
     * Adapted from [CodeWhispererCodeScanSession]
     */
    fun uploadPayload(payload: File): String {
        val sha256checksum: String = Base64.getEncoder().encodeToString(DigestUtils.sha256(FileInputStream(payload)))
        LOG.warn { "About to create an upload url" }
        if (isDisposed.get()) {
            throw AlreadyDisposedException("Disposed when about to create upload URL")
        }
        val clientAdaptor = GumbyClient.getInstance(sessionContext.project)
        val createUploadStartTime = Instant.now()
        val createUploadUrlResponse = try {
            clientAdaptor.createGumbyUploadUrl(sha256checksum)
        } catch (e: Exception) {
            CodetransformTelemetry.logApiError(
                codeTransformApiErrorMessage = e.message.toString(),
                codeTransformApiNames = CodeTransformApiNames.CreateUploadUrl,
                codeTransformSessionId = CodeTransformTelemetryState.instance.getSessionId(),
            )
            throw e // pass along error to callee
        }
        CodetransformTelemetry.logApiLatency(
            codeTransformApiNames = CodeTransformApiNames.CreateUploadUrl,
            codeTransformSessionId = CodeTransformTelemetryState.instance.getSessionId(),
            codeTransformRunTimeLatency = calculateTotalLatency(createUploadStartTime, Instant.now()),
            codeTransformUploadId = createUploadUrlResponse.uploadId()
        )

        LOG.warn {
            "Uploading zip with checksum $sha256checksum using uploadId: ${
                createUploadUrlResponse.uploadId()
            } and size ${(payload.length() / 1000).toInt()}kB"
        }
        if (isDisposed.get()) {
            throw AlreadyDisposedException("Disposed when about to upload zip to s3")
        }
        val uploadStartTime = Instant.now()
        try {
            uploadArtifactToS3(createUploadUrlResponse.uploadUrl(), payload, sha256checksum, createUploadUrlResponse.kmsKeyArn().orEmpty())
        } catch (e: Exception) {
            CodetransformTelemetry.logApiError(
                codeTransformApiErrorMessage = e.message.toString(),
                codeTransformApiNames = CodeTransformApiNames.UploadZip,
                codeTransformSessionId = CodeTransformTelemetryState.instance.getSessionId(),
            )
            throw e // pass along error to callee
        }
        if (!shouldStop.get()) {
            CodetransformTelemetry.logApiLatency(
                codeTransformApiNames = CodeTransformApiNames.UploadZip,
                codeTransformSessionId = CodeTransformTelemetryState.instance.getSessionId(),
                codeTransformRunTimeLatency = calculateTotalLatency(uploadStartTime, Instant.now()),
                codeTransformTotalByteSize = payload.length().toInt()
            )
            LOG.warn { "Upload complete" }
        }
        return createUploadUrlResponse.uploadId()
    }

    suspend fun pollUntilJobCompletion(
        jobId: JobId,
        jobTransitionHandler: (currentStatus: TransformationStatus, migrationPlan: TransformationPlan?) -> Unit,
    ): CodeModernizerJobCompletedResult {
        try {
            state.currentJobId = jobId
            val apiStartTime = Instant.now()
            try {
                // add delay to avoid the throttling error
                delay(1000)
                val modernizationResult = clientAdaptor.getCodeModernizationJob(jobId.id)
                state.currentJobCreationTime = modernizationResult.transformationJob().creationTime()
                CodetransformTelemetry.logApiLatency(
                    codeTransformApiNames = CodeTransformApiNames.GetTransformation,
                    codeTransformSessionId = CodeTransformTelemetryState.instance.getSessionId(),
                    codeTransformRunTimeLatency = calculateTotalLatency(apiStartTime, Instant.now()),
                    codeTransformJobId = jobId.id,
                    codeTransformRequestId = modernizationResult.responseMetadata().requestId()
                )
            } catch (e: Exception) {
                CodetransformTelemetry.logApiError(
                    codeTransformApiNames = CodeTransformApiNames.GetTransformation,
                    codeTransformSessionId = CodeTransformTelemetryState.instance.getSessionId(),
                    codeTransformJobId = jobId.id,
                    codeTransformApiErrorMessage = e.message.toString()
                )
                throw e // pass along the error to the parent callee
            }

            val modernizationPlan = when (val result = awaitModernizationPlan(jobId, jobTransitionHandler)) {
                is AwaitModernizationPlanResult.Success -> result.plan
                is AwaitModernizationPlanResult.BuildFailed -> return CodeModernizerJobCompletedResult.JobFailedInitialBuild(jobId, result.failureReason)
                is AwaitModernizationPlanResult.Failure -> return CodeModernizerJobCompletedResult.JobFailed(
                    jobId,
                    result.failureReason,
                )

                is AwaitModernizationPlanResult.UnknownStatusWhenPolling -> return CodeModernizerJobCompletedResult.JobFailed(
                    jobId,
                    message("codemodernizer.notification.warn.unknown_status_response"),
                )
            }

            state.transformationPlan = modernizationPlan
            tryOpenTransformationPlanEditor()

            var isPartialSuccess = false
            val result = jobId.pollTransformationStatusAndPlan(
                succeedOn = setOf(
                    TransformationStatus.COMPLETED,
                    TransformationStatus.STOPPING,
                    TransformationStatus.STOPPED,
                    TransformationStatus.PARTIALLY_COMPLETED,
                ),
                failOn = setOf(
                    TransformationStatus.FAILED,
                    TransformationStatus.UNKNOWN_TO_SDK_VERSION,
                ),
                clientAdaptor,
                initialPollingSleepDurationMillis,
                totalPollingSleepDurationMillis,
                isDisposed,
                sessionContext.project,
            ) { old, new, plan ->
                // Always refresh the dev tool tree so status will be up-to-date
                state.currentJobStatus = new
                state.transformationPlan = plan
                sessionContext.project.refreshCwQTree()
                if (new == TransformationStatus.PARTIALLY_COMPLETED) {
                    isPartialSuccess = true
                }
                val instant = Instant.now()
                state.updateJobHistory(sessionContext, new.name, instant)
                setCurrentJobStopTime(new, instant)
                jobTransitionHandler(new, plan)
                LOG.warn { "in awaitJobCompletion, state changed for job $jobId: $old -> $new" }
            }
            return when {
                isPartialSuccess -> CodeModernizerJobCompletedResult.JobPartiallySucceeded(jobId, sessionContext.targetJavaVersion)
                result.succeeded -> CodeModernizerJobCompletedResult.JobCompletedSuccessfully(jobId)
                result.state == TransformationStatus.UNKNOWN_TO_SDK_VERSION -> CodeModernizerJobCompletedResult.JobFailed(
                    jobId,
                    message("codemodernizer.notification.warn.unknown_status_response")
                )

                else -> CodeModernizerJobCompletedResult.JobFailed(jobId, result.jobDetails?.reason())
            }
        } catch (e: Exception) {
            return when (e) {
                is AlreadyDisposedException, is CancellationException -> {
                    LOG.warn { "The session was disposed while polling for job details." }
                    CodeModernizerJobCompletedResult.ManagerDisposed
                }

                else -> {
                    LOG.warn(e) { e.message.toString() }
                    CodeModernizerJobCompletedResult.RetryableFailure(
                        jobId,
                        message("codemodernizer.notification.info.modernize_failed.connection_failed", e.localizedMessage),
                    )
                }
            }
        }
    }

    fun stopTransformation(transformationId: String?): Boolean {
        shouldStop.set(true) // allows the zipping and upload to cancel
        return if (transformationId != null) {
            // Means job exists in backend and we have to call the stop api
            try {
                val apiStartTime = Instant.now()
                val result = clientAdaptor.stopTransformation(transformationId)
                CodetransformTelemetry.logApiLatency(
                    codeTransformApiNames = CodeTransformApiNames.StopTransformation,
                    codeTransformSessionId = CodeTransformTelemetryState.instance.getSessionId(),
                    codeTransformRunTimeLatency = calculateTotalLatency(apiStartTime, Instant.now()),
                    codeTransformJobId = transformationId,
                    codeTransformRequestId = result.responseMetadata().requestId()
                )
                return true
            } catch (e: Exception) {
                CodetransformTelemetry.logApiError(
                    codeTransformApiNames = CodeTransformApiNames.StopTransformation,
                    codeTransformSessionId = CodeTransformTelemetryState.instance.getSessionId(),
                    codeTransformApiErrorMessage = e.message.toString(),
                    codeTransformJobId = transformationId,
                )
                throw e // pass along error to callee
            }
        } else {
            true // We did not yet call the start API so no need to call the stop job api
        }
    }

    fun getTransformationPlan(): TransformationPlan? = state.transformationPlan

    fun setCurrentJobStopTime(status: TransformationStatus, instant: Instant) {
        if (status in setOf(
                TransformationStatus.COMPLETED, // successfully transformed
                TransformationStatus.STOPPED, // manually stopped,
                TransformationStatus.PARTIALLY_COMPLETED, // partially successfully transformed
                TransformationStatus.FAILED, // unable to generate transformation plan
                TransformationStatus.UNKNOWN_TO_SDK_VERSION,
            )
        ) {
            state.currentJobStopTime = instant
        }
    }

    private fun setCurrentJobSummary(status: TransformationStatus) {
        state.transformationSummary ?: return
        if (status in setOf(
                TransformationStatus.COMPLETED,
                TransformationStatus.STOPPED,
                TransformationStatus.PARTIALLY_COMPLETED,
                TransformationStatus.FAILED,
            )
        ) {
            // val response = TODO()
            val summary = TransformationSummary(
                """
            # Transformation summary

            This is pretty
            and now it has been updated from api call...
                """.trimIndent()
            )
            state.transformationSummary = summary
        }
    }

    fun tryOpenTransformationPlanEditor() {
        val transformationPlan = getTransformationPlan()
        if (transformationPlan != null) {
            runInEdt {
                CodeModernizerPlanEditorProvider.openEditor(
                    sessionContext.project,
                    transformationPlan,
                    sessionContext.project.getModuleOrProjectNameForFile(sessionContext.configurationFile),
                    sessionContext.sourceJavaVersion.description,
                )
            }
        }
    }

    companion object {
        private val LOG = getLogger<CodeModernizerSession>()
        const val APPLICATION_ZIP = "application/zip"
        const val CONTENT_SHA256 = "x-amz-checksum-sha256"
    }

    override fun dispose() {
        isDisposed.set(true)
    }

    fun getActiveJobId() = state.currentJobId
    fun fetchPlan(lastJobId: JobId) = clientAdaptor.getCodeModernizationPlan(lastJobId)

    fun didJobStart() = state.currentJobId != null
}
