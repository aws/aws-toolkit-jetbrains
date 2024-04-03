// Copyright 2023 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.codemodernizer

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.runInEdt
import com.intellij.serviceContainer.AlreadyDisposedException
import kotlinx.coroutines.delay
import org.apache.commons.codec.digest.DigestUtils
import software.amazon.awssdk.services.codewhispererruntime.model.StartTransformationResponse
import software.amazon.awssdk.services.codewhispererruntime.model.TransformationJob
import software.amazon.awssdk.services.codewhispererruntime.model.TransformationLanguage
import software.amazon.awssdk.services.codewhispererruntime.model.TransformationPlan
import software.amazon.awssdk.services.codewhispererruntime.model.TransformationStatus
import software.aws.toolkits.core.utils.error
import software.aws.toolkits.core.utils.getLogger
import software.aws.toolkits.core.utils.info
import software.aws.toolkits.core.utils.warn
import software.aws.toolkits.jetbrains.core.explorer.refreshCwQTree
import software.aws.toolkits.jetbrains.services.codemodernizer.client.GumbyClient
import software.aws.toolkits.jetbrains.services.codemodernizer.model.AwaitModernizationPlanResult
import software.aws.toolkits.jetbrains.services.codemodernizer.model.CodeModernizerException
import software.aws.toolkits.jetbrains.services.codemodernizer.model.CodeModernizerJobCompletedResult
import software.aws.toolkits.jetbrains.services.codemodernizer.model.CodeModernizerSessionContext
import software.aws.toolkits.jetbrains.services.codemodernizer.model.CodeModernizerStartJobResult
import software.aws.toolkits.jetbrains.services.codemodernizer.model.JobId
import software.aws.toolkits.jetbrains.services.codemodernizer.model.MavenCopyCommandsResult
import software.aws.toolkits.jetbrains.services.codemodernizer.model.ZipCreationResult
import software.aws.toolkits.jetbrains.services.codemodernizer.plan.CodeModernizerPlanEditorProvider
import software.aws.toolkits.jetbrains.services.codemodernizer.state.CodeModernizerSessionState
import software.aws.toolkits.jetbrains.services.codewhisperer.codescan.CodeWhispererCodeScanSession
import software.aws.toolkits.resources.message
import software.aws.toolkits.telemetry.CodeTransformApiNames
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.time.Instant
import java.util.Base64
import java.util.concurrent.CancellationException
import java.util.concurrent.atomic.AtomicBoolean

const val ZIP_SOURCES_PATH = "sources"
const val BUILD_LOG_PATH = "build-logs.txt"
const val UPLOAD_ZIP_MANIFEST_VERSION = 1.0F
const val MAX_ZIP_SIZE = 1000000000 // 1GB

class CodeModernizerSession(
    val sessionContext: CodeModernizerSessionContext,
    val initialPollingSleepDurationMillis: Long = 2000,
    val totalPollingSleepDurationMillis: Long = 5000,
) : Disposable {
    private val clientAdaptor = GumbyClient.getInstance(sessionContext.project)
    private val state = CodeModernizerSessionState.getInstance(sessionContext.project)
    private val isDisposed = AtomicBoolean(false)
    private val shouldStop = AtomicBoolean(false)
    private val telemetry = CodeTransformTelemetryManager.getInstance(sessionContext.project)

    private var mvnBuildResult: MavenCopyCommandsResult? = null
    private var transformResult: CodeModernizerJobCompletedResult? = null

    fun getLastMvnBuildResult(): MavenCopyCommandsResult? = mvnBuildResult

    fun setLastMvnBuildResult(result: MavenCopyCommandsResult) {
        mvnBuildResult = result
    }

    fun getLastTransformResult(): CodeModernizerJobCompletedResult? = transformResult

    fun setLastTransformResult(result: CodeModernizerJobCompletedResult) {
        transformResult = result
    }

    fun getDependenciesUsingMaven(): MavenCopyCommandsResult = sessionContext.getDependenciesUsingMaven()

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
    fun createModernizationJob(copyResult: MavenCopyCommandsResult): CodeModernizerStartJobResult {
        LOG.info { "Starting Modernization Job" }
        val payload: File?

        try {
            if (isDisposed.get()) {
                LOG.warn { "Disposed when about to create zip to upload" }
                return CodeModernizerStartJobResult.Disposed
            }
            val startTime = Instant.now()
            val result = sessionContext.createZipWithModuleFiles(copyResult)

            if (result is ZipCreationResult.Missing1P) {
                return CodeModernizerStartJobResult.CancelledMissingDependencies
            }

            payload = result.payload

            val payloadSize = payload.length().toInt()

            telemetry.jobCreateZipEndTime(payloadSize, startTime)

            if (payloadSize > MAX_ZIP_SIZE) {
                return CodeModernizerStartJobResult.CancelledZipTooLarge
            }
        } catch (e: Exception) {
            val errorMessage = "Failed to create zip"
            LOG.error(e) { errorMessage }
            telemetry.error(errorMessage)
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
            state.putJobHistory(sessionContext, TransformationStatus.STARTED, startJobResponse.transformationJobId())
            state.currentJobStatus = TransformationStatus.STARTED
            CodeModernizerStartJobResult.Started(JobId(startJobResponse.transformationJobId()))
        } catch (e: AlreadyDisposedException) {
            LOG.warn { e.localizedMessage }
            return CodeModernizerStartJobResult.Disposed
        } catch (e: IOException) {
            if (shouldStop.get()) {
                // Cancelling during S3 upload will cause IOException of "not enough data written",
                // so no need to show an IDE error for it
                LOG.warn { "Job was cancelled by user before start job was called" }
                CodeModernizerStartJobResult.Cancelled
            } else {
                val errorMessage = "Failed to start job"
                LOG.error(e) { errorMessage }
                state.putJobHistory(sessionContext, TransformationStatus.FAILED)
                state.currentJobStatus = TransformationStatus.FAILED
                telemetry.error(errorMessage)
                CodeModernizerStartJobResult.UnableToStartJob(e.message.toString())
            }
        } catch (e: Exception) {
            val errorMessage = "Failed to start job"
            LOG.error(e) { errorMessage }
            state.putJobHistory(sessionContext, TransformationStatus.FAILED)
            state.currentJobStatus = TransformationStatus.FAILED
            telemetry.error(errorMessage)
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
            LOG.info { "Waiting for Transformation Plan for Modernization Job [$jobId]. State changed: $old -> $new" }
            state.currentJobStatus = new
            sessionContext.project.refreshCwQTree()
            val instant = Instant.now()
            state.updateJobHistory(sessionContext, new, instant)
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
            result.state == TransformationStatus.STOPPED -> AwaitModernizationPlanResult.Stopped
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
        LOG.info { "Starting job with uploadId [$uploadId] for $sourceLanguage -> $targetLanguage" }
        return clientAdaptor.startCodeModernization(uploadId, sourceLanguage, targetLanguage)
    }

    /**
     * Will perform a single call, checking if a modernization job is finished.
     */
    fun getJobDetails(jobId: JobId): TransformationJob {
        LOG.info { "Getting job details." }
        return clientAdaptor.getCodeModernizationJob(jobId.id).transformationJob()
    }

    fun getTransformPlanDetails(jobId: JobId): TransformationPlan {
        LOG.info { "Getting transform plan details." }
        return clientAdaptor.getCodeModernizationPlan(jobId).transformationPlan()
    }

    /**
     * This will resume the job, i.e. it will resume the main job loop kicked of by [createModernizationJob]
     */
    fun resumeJob(startTime: Instant, jobId: JobId) = state.putJobHistory(sessionContext, TransformationStatus.STARTED, jobId.id, startTime)

    /**
     * Adapted from [CodeWhispererCodeScanSession]
     */
    fun uploadPayload(payload: File): String {
        val sha256checksum: String = Base64.getEncoder().encodeToString(DigestUtils.sha256(FileInputStream(payload)))
        if (isDisposed.get()) {
            throw AlreadyDisposedException("Disposed when about to create upload URL")
        }
        val clientAdaptor = GumbyClient.getInstance(sessionContext.project)
        val createUploadUrlResponse = clientAdaptor.createGumbyUploadUrl(sha256checksum)

        LOG.info {
            "Uploading zip with checksum $sha256checksum using uploadId: ${
                createUploadUrlResponse.uploadId()
            } and size ${(payload.length() / 1000).toInt()}kB"
        }
        if (isDisposed.get()) {
            throw AlreadyDisposedException("Disposed when about to upload zip to s3")
        }
        val uploadStartTime = Instant.now()
        try {
            clientAdaptor.uploadArtifactToS3(
                createUploadUrlResponse.uploadUrl(),
                payload,
                sha256checksum,
                createUploadUrlResponse.kmsKeyArn().orEmpty(),
            ) { shouldStop.get() }
        } catch (e: Exception) {
            val errorMessage = "Unexpected error when uploading artifact to S3: ${e.localizedMessage}"
            telemetry.apiError(errorMessage, CodeTransformApiNames.UploadZip, createUploadUrlResponse.uploadId())
            throw e // pass along error to callee
        }
        if (!shouldStop.get()) {
            telemetry.logApiLatency(
                CodeTransformApiNames.UploadZip,
                uploadStartTime,
                payload.length().toInt(),
                createUploadUrlResponse.responseMetadata().requestId(),
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

            // add delay to avoid the throttling error
            delay(1000)
            val modernizationResult = clientAdaptor.getCodeModernizationJob(jobId.id)
            state.currentJobCreationTime = modernizationResult.transformationJob().creationTime()

            val modernizationPlan = when (val result = awaitModernizationPlan(jobId, jobTransitionHandler)) {
                is AwaitModernizationPlanResult.Success -> result.plan
                is AwaitModernizationPlanResult.BuildFailed -> return CodeModernizerJobCompletedResult.JobFailedInitialBuild(jobId, result.failureReason)
                is AwaitModernizationPlanResult.Failure -> return CodeModernizerJobCompletedResult.JobFailed(
                    jobId,
                    result.failureReason,
                )
                is AwaitModernizationPlanResult.Stopped -> return CodeModernizerJobCompletedResult.Stopped
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
                state.updateJobHistory(sessionContext, new, instant)
                setCurrentJobStopTime(new, instant)
                jobTransitionHandler(new, plan)
                LOG.info { "Waiting for Modernization Job [$jobId] to complete. State changed for job: $old -> $new" }
            }
            return when {
                result.state == TransformationStatus.STOPPED -> CodeModernizerJobCompletedResult.Stopped
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
                    LOG.error(e) { e.message.toString() }
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
            // Means job exists in backend, and we have to call the stop api
            clientAdaptor.stopTransformation(transformationId)
            return true
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
    }

    override fun dispose() {
        isDisposed.set(true)
    }

    fun getActiveJobId() = state.currentJobId
    fun fetchPlan(lastJobId: JobId) = clientAdaptor.getCodeModernizationPlan(lastJobId)

    fun didJobStart() = state.currentJobId != null
}
