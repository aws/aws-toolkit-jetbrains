// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.codewhisperer.codescan
import com.intellij.openapi.project.Project
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withTimeout
import software.amazon.awssdk.services.codewhispererruntime.model.CodeFixJobStatus
import software.amazon.awssdk.services.codewhispererruntime.model.CodeFixUploadContext
import software.amazon.awssdk.services.codewhispererruntime.model.CreateUploadUrlRequest
import software.amazon.awssdk.services.codewhispererruntime.model.CreateUploadUrlResponse
import software.amazon.awssdk.services.codewhispererruntime.model.GetCodeFixJobRequest
import software.amazon.awssdk.services.codewhispererruntime.model.GetCodeFixJobResponse
import software.amazon.awssdk.services.codewhispererruntime.model.Position
import software.amazon.awssdk.services.codewhispererruntime.model.Range
import software.amazon.awssdk.services.codewhispererruntime.model.RecommendationsWithReferencesPreference
import software.amazon.awssdk.services.codewhispererruntime.model.StartCodeFixJobRequest
import software.amazon.awssdk.services.codewhispererruntime.model.StartCodeFixJobResponse
import software.amazon.awssdk.services.codewhispererruntime.model.UploadContext
import software.amazon.awssdk.services.codewhispererruntime.model.UploadIntent
import software.aws.toolkits.core.utils.createTemporaryZipFile
import software.aws.toolkits.core.utils.debug
import software.aws.toolkits.core.utils.error
import software.aws.toolkits.core.utils.getLogger
import software.aws.toolkits.core.utils.info
import software.aws.toolkits.core.utils.putNextEntry
import software.aws.toolkits.jetbrains.services.amazonq.profile.QRegionProfileManager
import software.aws.toolkits.jetbrains.services.codewhisperer.credentials.CodeWhispererClientAdaptor
import software.aws.toolkits.jetbrains.services.codewhisperer.util.CodeWhispererConstants
import software.aws.toolkits.jetbrains.services.codewhisperer.util.CodeWhispererZipUploadManager
import software.aws.toolkits.jetbrains.services.codewhisperer.util.getTelemetryErrorMessage
import software.aws.toolkits.jetbrains.settings.CodeWhispererSettings
import software.aws.toolkits.resources.message
import java.io.File
import java.nio.file.Path
import java.time.Instant
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext
import kotlin.io.path.pathString

class AmazonQCodeFixSession(val project: Project) {
    private fun now() = Instant.now().toEpochMilli()

    private val clientAdaptor get() = CodeWhispererClientAdaptor.getInstance(project)

    suspend fun runCodeFixWorkflow(issue: CodeWhispererCodeScanIssue): CodeFixResponse {
        val currentCoroutineContext = coroutineContext

        try {
            currentCoroutineContext.ensureActive()

            /**
             * * Step 1: Generate zip
             * */
            val sourceZip = withTimeout(CodeWhispererConstants.CODE_FIX_CREATE_PAYLOAD_TIMEOUT_IN_SECONDS) {
                zipFile(issue.file.toNioPath())
            }

            /**
             * * Step 2: Create upload URL and upload the zip
             * */

            currentCoroutineContext.ensureActive()
            val codeFixName = issue.findingId
            if (!sourceZip.exists()) {
                return CodeFixResponse(
                    getCodeFixJobResponse = null,
                    failureResponse = message("codewhisperer.codefix.invalid_zip_error")
                )
            }

            val sourceZipUploadResponse =
                CodeWhispererZipUploadManager.getInstance(project).createUploadUrlAndUpload(
                    sourceZip,
                    "SourceCode",
                    CodeWhispererConstants.UploadTaskType.CODE_FIX,
                    codeFixName,
                    CodeWhispererConstants.FeatureName.CODE_REVIEW
                )

            /**
             * * Step 3: Create code fix
             * */
            currentCoroutineContext.ensureActive()
            val issueRange = Range.builder().start(
                Position.builder()
                    .line(issue.startLine)
                    .character(0)
                    .build()
            )
                .end(
                    Position.builder()
                        .line(issue.endLine)
                        .character(0)
                        .build()
                )
                .build()

            val createCodeFixResponse = createCodeFixJob(
                sourceZipUploadResponse.uploadId(),
                issueRange,
                issue.recommendation.text,
                codeFixName,
                issue.ruleId
            )
            val codeFixJobId = createCodeFixResponse.jobId()
            LOG.info { "Code fix job created: $codeFixJobId" }
            /**
             * * Step 4: polling for code fix
             */
            currentCoroutineContext.ensureActive()
            val jobStatus = pollCodeFixJobStatus(createCodeFixResponse.jobId(), currentCoroutineContext)
            if (jobStatus == CodeFixJobStatus.FAILED) {
                LOG.debug { "Code fix generation failed." }
                return CodeFixResponse(
                    getCodeFixJobResponse = null,
                    failureResponse = message("codewhisperer.codefix.create_code_fix_error")
                )
            }
            /**
             * Step 5: Process and render code fix results
             */
            currentCoroutineContext.ensureActive()
            LOG.debug { "Code fix job succeeded and start processing result." }
            val getCodeFixJobResponse = getCodeFixJob(createCodeFixResponse.jobId())
            return CodeFixResponse(
                getCodeFixJobResponse = getCodeFixJobResponse,
                failureResponse = null,
                jobId = codeFixJobId
            )
        } catch (e: Exception) {
            LOG.error(e) { "Code scan session failed: ${e.message}" }
            val timeoutMessage = message("codewhisperer.codefix.code_fix_job_timed_out")
            return CodeFixResponse(
                getCodeFixJobResponse = null,
                failureResponse = when {
                    e is CodeWhispererCodeFixException && e.message == timeoutMessage -> timeoutMessage
                    else -> message("codewhisperer.codefix.create_code_fix_error")
                }
            )
        }
    }

    fun createUploadUrl(md5Content: String, artifactType: String, codeFixName: String): CreateUploadUrlResponse = try {
        clientAdaptor.createUploadUrl(
            CreateUploadUrlRequest.builder()
                .contentMd5(md5Content)
                .artifactType(artifactType)
                .uploadIntent(UploadIntent.CODE_FIX_GENERATION)
                .uploadContext(UploadContext.fromCodeFixUploadContext(CodeFixUploadContext.builder().codeFixName(codeFixName).build()))
                .profileArn(QRegionProfileManager.getInstance().activeProfile(project)?.arn)
                .build()
        )
    } catch (e: Exception) {
        LOG.debug { "Create Upload URL failed: ${e.message}" }
        val errorMessage = getTelemetryErrorMessage(e, featureUseCase = CodeWhispererConstants.FeatureName.CODE_REVIEW)
        throw codeScanServerException("CreateUploadUrlException: $errorMessage")
    }

    private fun createCodeFixJob(
        uploadId: String,
        snippetRange: Range,
        description: String,
        codeFixName: String? = null,
        ruleId: String? = null,
    ): StartCodeFixJobResponse {
        val includeCodeWithReference = if (CodeWhispererSettings.getInstance().isIncludeCodeWithReference()) {
            RecommendationsWithReferencesPreference.ALLOW
        } else {
            RecommendationsWithReferencesPreference.BLOCK
        }

        val request = StartCodeFixJobRequest.builder()
            .uploadId(uploadId)
            .snippetRange(snippetRange)
            .codeFixName(codeFixName)
            .ruleId(ruleId)
            .description(description)
            .referenceTrackerConfiguration { it.recommendationsWithReferences(includeCodeWithReference) }
            .profileArn(QRegionProfileManager.getInstance().activeProfile(project)?.arn)
            .build()

        return try {
            val response = clientAdaptor.startCodeFixJob(request)
            LOG.debug {
                "Code Fix Request id: ${response.responseMetadata().requestId()} " +
                    "and Code Fix Job id: ${response.jobId()}"
            }
            response
        } catch (e: Exception) {
            LOG.error { "Failed creating code fix job ${e.message}" }
            throw CodeWhispererCodeFixException(message("codewhisperer.codefix.create_code_fix_error"))
        }
    }

    private suspend fun pollCodeFixJobStatus(jobId: String, currentCoroutineContext: CoroutineContext): CodeFixJobStatus {
        val pollingStartTime = now()
        delay(CodeWhispererConstants.CODE_FIX_POLLING_INTERVAL_IN_SECONDS)
        var status = CodeFixJobStatus.IN_PROGRESS
        while (true) {
            currentCoroutineContext.ensureActive()

            val request = GetCodeFixJobRequest.builder()
                .jobId(jobId)
                .profileArn(QRegionProfileManager.getInstance().activeProfile(project)?.arn)
                .build()

            val response = clientAdaptor.getCodeFixJob(request)
            LOG.debug { "Request id: ${response.responseMetadata().requestId()}" }

            if (response.jobStatus() != CodeFixJobStatus.IN_PROGRESS) {
                status = response.jobStatus()
                LOG.debug { "Code fix job status: ${status.name}" }
                LOG.debug { "Complete polling code fix job status." }
                break
            }

            currentCoroutineContext.ensureActive()
            delay(CodeWhispererConstants.CODE_FIX_POLLING_INTERVAL_IN_SECONDS)

            val elapsedTime = (now() - pollingStartTime) / CodeWhispererConstants.TOTAL_MILLIS_IN_SECOND // In seconds
            if (elapsedTime > CodeWhispererConstants.CODE_FIX_TIMEOUT_IN_SECONDS) {
                LOG.debug { "Code fix job status: ${status.name}" }
                LOG.debug { "Code fix job failed. Amazon Q timed out." }
                throw CodeWhispererCodeFixException(message("codewhisperer.codefix.code_fix_job_timed_out"))
            }
        }
        return status
    }

    private fun getCodeFixJob(jobId: String): GetCodeFixJobResponse {
        val response = clientAdaptor.getCodeFixJob(
            GetCodeFixJobRequest.builder()
                .profileArn(QRegionProfileManager.getInstance().activeProfile(project)?.arn)
                .jobId(jobId).build()
        )
        return response
    }
    private fun zipFile(file: Path): File = createTemporaryZipFile {
        try {
            LOG.debug { "Selected file for truncation: $file" }
            it.putNextEntry(file.toString(), file)
        } catch (e: Exception) {
            cannotFindFile("Zipping error: ${e.message}", file.pathString)
        }
    }.toFile()

    companion object {
        private val LOG = getLogger<AmazonQCodeFixSession>()
    }
    data class CodeFixResponse(val getCodeFixJobResponse: GetCodeFixJobResponse? = null, val failureResponse: String? = null, val jobId: String? = null)
}
