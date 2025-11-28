// Copyright 2022 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.codewhisperer.codescan

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.time.withTimeout
import kotlinx.coroutines.withContext
import software.amazon.awssdk.services.codewhispererruntime.model.ArtifactType
import software.amazon.awssdk.services.codewhispererruntime.model.CodeAnalysisFindingsSchema
import software.amazon.awssdk.services.codewhispererruntime.model.CodeAnalysisStatus
import software.amazon.awssdk.services.codewhispererruntime.model.CodeWhispererRuntimeException
import software.amazon.awssdk.services.codewhispererruntime.model.CreateUploadUrlResponse
import software.amazon.awssdk.services.codewhispererruntime.model.GetCodeAnalysisRequest
import software.amazon.awssdk.services.codewhispererruntime.model.GetCodeAnalysisResponse
import software.amazon.awssdk.services.codewhispererruntime.model.ListCodeAnalysisFindingsRequest
import software.amazon.awssdk.services.codewhispererruntime.model.ListCodeAnalysisFindingsResponse
import software.amazon.awssdk.services.codewhispererruntime.model.Reference
import software.amazon.awssdk.services.codewhispererruntime.model.StartCodeAnalysisRequest
import software.amazon.awssdk.services.codewhispererruntime.model.StartCodeAnalysisResponse
import software.aws.toolkits.core.utils.Waiters.waitUntil
import software.aws.toolkits.core.utils.debug
import software.aws.toolkits.core.utils.error
import software.aws.toolkits.core.utils.getLogger
import software.aws.toolkits.core.utils.info
import software.aws.toolkits.jetbrains.services.amazonq.profile.QRegionProfileManager
import software.aws.toolkits.jetbrains.services.codewhisperer.codescan.sessionconfig.CodeScanSessionConfig
import software.aws.toolkits.jetbrains.services.codewhisperer.codescan.sessionconfig.PayloadContext
import software.aws.toolkits.jetbrains.services.codewhisperer.credentials.CodeWhispererClientAdaptor
import software.aws.toolkits.jetbrains.services.codewhisperer.explorer.CodeWhispererExplorerActionManager
import software.aws.toolkits.jetbrains.services.codewhisperer.model.CodeScanResponseContext
import software.aws.toolkits.jetbrains.services.codewhisperer.model.CreateUploadUrlServiceInvocationContext
import software.aws.toolkits.jetbrains.services.codewhisperer.util.CodeWhispererConstants
import software.aws.toolkits.jetbrains.services.codewhisperer.util.CodeWhispererConstants.CODE_SCAN_POLLING_INTERVAL_IN_SECONDS
import software.aws.toolkits.jetbrains.services.codewhisperer.util.CodeWhispererConstants.FILE_SCANS_THROTTLING_MESSAGE
import software.aws.toolkits.jetbrains.services.codewhisperer.util.CodeWhispererConstants.FILE_SCAN_INITIAL_POLLING_INTERVAL_IN_SECONDS
import software.aws.toolkits.jetbrains.services.codewhisperer.util.CodeWhispererConstants.PROJECT_SCANS_THROTTLING_MESSAGE
import software.aws.toolkits.jetbrains.services.codewhisperer.util.CodeWhispererConstants.PROJECT_SCAN_INITIAL_POLLING_INTERVAL_IN_SECONDS
import software.aws.toolkits.jetbrains.services.codewhisperer.util.CodeWhispererConstants.TOTAL_BYTES_IN_KB
import software.aws.toolkits.jetbrains.services.codewhisperer.util.CodeWhispererConstants.TOTAL_MILLIS_IN_SECOND
import software.aws.toolkits.jetbrains.services.codewhisperer.util.CodeWhispererUtil.notifyErrorCodeWhispererUsageLimit
import software.aws.toolkits.jetbrains.services.codewhisperer.util.CodeWhispererZipUploadManager
import software.aws.toolkits.jetbrains.services.codewhisperer.util.getTelemetryErrorMessage
import software.aws.toolkits.jetbrains.utils.assertIsNonDispatchThread
import software.aws.toolkits.resources.message
import software.aws.toolkits.telemetry.CodewhispererLanguage
import java.nio.file.Path
import java.time.Duration
import java.time.Instant
import java.util.UUID
import kotlin.coroutines.coroutineContext

class CodeWhispererCodeScanSession(val sessionContext: CodeScanSessionContext) {
    private val clientToken: UUID = UUID.randomUUID()
    private val urlResponse = mutableMapOf<ArtifactType, CreateUploadUrlResponse>()

    private val clientAdaptor = CodeWhispererClientAdaptor.getInstance(sessionContext.project)

    private fun now() = Instant.now().toEpochMilli()

    /**
     * Note that this function makes network calls and needs to be run from a background thread.
     * Runs a code scan session which comprises the following steps:
     *  1. Generate truncation (zip files) based on the truncation in the session context.
     *  2. CreateUploadURL to upload the context.
     *  3. Upload the zip files using the URL
     *  4. Call createCodeScan to start a code scan
     *  5. Keep polling the API GetCodeScan to wait for results for a given timeout period.
     *  6. Return the results from the ListCodeScan API.
     */
    suspend fun run(): CodeScanResponse {
        var codeScanResponseContext = defaultCodeScanResponseContext()
        val currentCoroutineContext = coroutineContext
        try {
            assertIsNonDispatchThread()
            currentCoroutineContext.ensureActive()
            val startTime = now()
            val (payloadContext, sourceZip) = withTimeout(Duration.ofSeconds(sessionContext.sessionConfig.createPayloadTimeoutInSeconds())) {
                sessionContext.sessionConfig.createPayload()
            }

            if (isProjectScope()) {
                LOG.debug {
                    "Total size of source payload in KB: ${payloadContext.srcPayloadSize * 1.0 / TOTAL_BYTES_IN_KB} \n" +
                        "Total size of source zip file in KB: ${payloadContext.srcZipFileSize * 1.0 / TOTAL_BYTES_IN_KB} \n" +
                        "Total number of lines reviewed: ${payloadContext.totalLines} \n" +
                        "Total number of files included in payload: ${payloadContext.totalFiles} \n" +
                        "Total time taken for creating payload: ${payloadContext.totalTimeInMilliseconds * 1.0 / TOTAL_MILLIS_IN_SECOND} seconds\n" +
                        "Payload context language: ${payloadContext.language}"
                }
            }
            codeScanResponseContext = codeScanResponseContext.copy(payloadContext = payloadContext)

            // 2 & 3. CreateUploadURL and upload the context.
            currentCoroutineContext.ensureActive()
            val artifactsUploadStartTime = now()
            val codeScanName = UUID.randomUUID().toString()

            val taskType = if (isAutoScan()) {
                CodeWhispererConstants.UploadTaskType.SCAN_FILE
            } else {
                CodeWhispererConstants.UploadTaskType.SCAN_PROJECT
            }

            val sourceZipUploadResponse =
                CodeWhispererZipUploadManager.getInstance(sessionContext.project).createUploadUrlAndUpload(
                    sourceZip,
                    "SourceCode",
                    taskType,
                    codeScanName,
                    CodeWhispererConstants.FeatureName.CODE_REVIEW
                )
            if (isProjectScope()) {
                LOG.debug {
                    "Successfully uploaded source zip to s3: " +
                        "Upload id: ${sourceZipUploadResponse.uploadId()} " +
                        "Request id: ${sourceZipUploadResponse.responseMetadata().requestId()}"
                }
            }
            urlResponse[ArtifactType.SOURCE_CODE] = sourceZipUploadResponse
            currentCoroutineContext.ensureActive()
            val artifactsUploadDuration = now() - artifactsUploadStartTime
            codeScanResponseContext = codeScanResponseContext.copy(
                serviceInvocationContext = codeScanResponseContext.serviceInvocationContext.copy(artifactsUploadDuration = artifactsUploadDuration)
            )

            // 4. Call createCodeScan to start a code scan
            currentCoroutineContext.ensureActive()
            val serviceInvocationStartTime = now()
            val createCodeScanResponse = createCodeScan(payloadContext.language.toString(), codeScanName)
            if (isProjectScope()) {
                LOG.debug {
                    "Successfully created code review with " +
                        "status: ${createCodeScanResponse.status()} " +
                        "for request id: ${createCodeScanResponse.responseMetadata().requestId()}"
                }
            }
            var codeScanStatus = createCodeScanResponse.status()
            if (codeScanStatus == CodeAnalysisStatus.FAILED) {
                if (isProjectScope()) {
                    LOG.debug {
                        "CodeWhisperer service error occurred. Something went wrong when creating a code review: $createCodeScanResponse " +
                            "Status: ${createCodeScanResponse.status()} for request id: ${createCodeScanResponse.responseMetadata().requestId()}"
                    }
                }
                val errorMessage = createCodeScanResponse.errorMessage()?.let { it } ?: message("codewhisperer.codescan.run_scan_error_telemetry")
                codeScanFailed(errorMessage)
            }
            val jobId = createCodeScanResponse.jobId()
            codeScanResponseContext = codeScanResponseContext.copy(codeScanJobId = jobId)
            if (isProjectScope()) {
                delay(PROJECT_SCAN_INITIAL_POLLING_INTERVAL_IN_SECONDS * TOTAL_MILLIS_IN_SECOND)
            } else {
                delay(FILE_SCAN_INITIAL_POLLING_INTERVAL_IN_SECONDS * TOTAL_MILLIS_IN_SECOND)
            }

            // 5. Keep polling the API GetCodeScan to wait for results for a given timeout period.
            waitUntil(
                succeedOn = { codeScanStatus == CodeAnalysisStatus.COMPLETED },
                maxDuration = Duration.ofSeconds(sessionContext.sessionConfig.overallJobTimeoutInSeconds())
            ) {
                currentCoroutineContext.ensureActive()
                val elapsedTime = (now() - startTime) * 1.0 / TOTAL_MILLIS_IN_SECOND
                if (isProjectScope()) {
                    LOG.debug { "Waiting for code review to complete. Elapsed time: $elapsedTime sec." }
                }
                val getCodeScanResponse = getCodeScan(jobId)
                codeScanStatus = getCodeScanResponse.status()
                if (isProjectScope()) {
                    LOG.debug {
                        "Get code review status: ${getCodeScanResponse.status()}, " +
                            "request id: ${getCodeScanResponse.responseMetadata().requestId()}"
                    }
                }
                delay(CODE_SCAN_POLLING_INTERVAL_IN_SECONDS * TOTAL_MILLIS_IN_SECOND)
                if (codeScanStatus == CodeAnalysisStatus.FAILED) {
                    if (isProjectScope()) {
                        LOG.debug {
                            "CodeWhisperer service error occurred. Something went wrong fetching results for code review: $getCodeScanResponse " +
                                "Status: ${getCodeScanResponse.status()} for request id: ${getCodeScanResponse.responseMetadata().requestId()}"
                        }
                    }
                    val errorMessage = getCodeScanResponse.errorMessage()?.let { it } ?: message("codewhisperer.codescan.run_scan_error_telemetry")
                    codeScanFailed(errorMessage)
                }
            }

            LOG.debug { "Code review completed successfully by Amazon Q." }

            // 6. Return the results from the ListCodeScan API.
            currentCoroutineContext.ensureActive()
            var listCodeScanFindingsResponse = listCodeScanFindings(jobId, null)
            val serviceInvocationDuration = now() - serviceInvocationStartTime
            codeScanResponseContext = codeScanResponseContext.copy(
                serviceInvocationContext = codeScanResponseContext.serviceInvocationContext.copy(serviceInvocationDuration = serviceInvocationDuration)
            )

            val documents = mutableListOf<String>()
            documents.add(listCodeScanFindingsResponse.codeAnalysisFindings())
            // coroutineContext helps to actively cancel the bigger projects quickly
            withContext(currentCoroutineContext) {
                while (listCodeScanFindingsResponse.nextToken() != null && currentCoroutineContext.isActive) {
                    listCodeScanFindingsResponse = listCodeScanFindings(jobId, listCodeScanFindingsResponse.nextToken())
                    documents.add(listCodeScanFindingsResponse.codeAnalysisFindings())
                }
            }

            if (isProjectScope()) {
                LOG.debug { "Rendering response to display code review results." }
            }
            currentCoroutineContext.ensureActive()
            val issues = mapToCodeScanIssues(documents, sessionContext.project, jobId).filter { it.isVisible }
            codeScanResponseContext = codeScanResponseContext.copy(codeScanTotalIssues = issues.count())
            codeScanResponseContext = codeScanResponseContext.copy(codeScanIssuesWithFixes = issues.count { it.suggestedFixes.isNotEmpty() })
            codeScanResponseContext = codeScanResponseContext.copy(reason = "Succeeded")
            return CodeScanResponse.Success(issues, codeScanResponseContext)
        } catch (e: Exception) {
            val exception = e as? CodeWhispererRuntimeException
            val awsError = exception?.awsErrorDetails()

            if (awsError != null) {
                if (awsError.errorCode() == "ThrottlingException" && awsError.errorMessage() != null) {
                    if (awsError.errorMessage()!!.contains(PROJECT_SCANS_THROTTLING_MESSAGE)) {
                        LOG.info { "Project reviews limit reached" }
                        notifyErrorCodeWhispererUsageLimit(sessionContext.project, true)
                    } else if (awsError.errorMessage()!!.contains(FILE_SCANS_THROTTLING_MESSAGE)) {
                        LOG.info { "File reviews limit reached" }
                        CodeWhispererExplorerActionManager.getInstance().setMonthlyQuotaForCodeScansExceeded(true)
                    }
                }
            }
            LOG.debug(e) {
                "Failed to run code review and display results. Caused by: ${e.message}, status code: ${awsError?.errorCode()}, " +
                    "exception: ${e::class.simpleName}, request ID: ${exception?.requestId()}" +
                    "Jetbrains IDE: ${ApplicationInfo.getInstance().fullApplicationName}, " +
                    "IDE version: ${ApplicationInfo.getInstance().apiVersion}, "
            }
            return CodeScanResponse.Failure(codeScanResponseContext, e)
        }
    }

    fun createCodeScan(language: String, codeScanName: String): StartCodeAnalysisResponse {
        val artifactsMap = mapOf(
            ArtifactType.SOURCE_CODE to urlResponse[ArtifactType.SOURCE_CODE]?.uploadId(),
            ArtifactType.BUILT_JARS to urlResponse[ArtifactType.BUILT_JARS]?.uploadId()
        ).filter { (_, v) -> v != null }

        val scope = when {
            sessionContext.codeAnalysisScope == CodeWhispererConstants.CodeAnalysisScope.FILE &&
                !sessionContext.sessionConfig.isInitiatedByChat() -> CodeWhispererConstants.CodeAnalysisScope.FILE
            else -> CodeWhispererConstants.CodeAnalysisScope.PROJECT
        }

        try {
            return clientAdaptor.createCodeScan(
                StartCodeAnalysisRequest.builder()
                    .clientToken(clientToken.toString())
                    .programmingLanguage {
                        it.languageName(
                            if (language == CodewhispererLanguage.Unknown.toString()) CodewhispererLanguage.Plaintext.toString() else language
                        )
                    }
                    .artifacts(artifactsMap)
                    .scope(scope.value)
                    .codeScanName(codeScanName)
                    .profileArn(QRegionProfileManager.getInstance().activeProfile(sessionContext.project)?.arn)
                    .build()
            )
        } catch (e: Exception) {
            LOG.debug { "Creating code review failed: ${e.message}" }
            val errorMessage = getTelemetryErrorMessage(e, featureUseCase = CodeWhispererConstants.FeatureName.CODE_REVIEW)
            throw codeScanServerException(errorMessage)
        }
    }

    fun getCodeScan(jobId: String): GetCodeAnalysisResponse = try {
        clientAdaptor.getCodeScan(
            GetCodeAnalysisRequest.builder()
                .jobId(jobId)
                .profileArn(QRegionProfileManager.getInstance().activeProfile(sessionContext.project)?.arn)
                .build()
        )
    } catch (e: Exception) {
        LOG.error(e) { "Getting code review failed: ${e.message}" }
        val errorMessage = getTelemetryErrorMessage(e, featureUseCase = CodeWhispererConstants.FeatureName.CODE_REVIEW)
        throw codeScanServerException("GetCodeReviewException: $errorMessage")
    }

    fun listCodeScanFindings(jobId: String, nextToken: String?): ListCodeAnalysisFindingsResponse = try {
        clientAdaptor.listCodeScanFindings(
            ListCodeAnalysisFindingsRequest.builder()
                .jobId(jobId)
                .codeAnalysisFindingsSchema(CodeAnalysisFindingsSchema.CODEANALYSIS_FINDINGS_1_0)
                .nextToken(nextToken)
                .profileArn(QRegionProfileManager.getInstance().activeProfile(sessionContext.project)?.arn)
                .build()
        )
    } catch (e: Exception) {
        LOG.debug { "Listing code review failed: ${e.message}" }
        val errorMessage = getTelemetryErrorMessage(e, featureUseCase = CodeWhispererConstants.FeatureName.CODE_REVIEW)
        throw codeScanServerException("ListCodeReviewFindingsException: $errorMessage")
    }

    fun mapToCodeScanIssues(recommendations: List<String>, project: Project, jobId: String): List<CodeWhispererCodeScanIssue> {
        val scanRecommendations = recommendations.flatMap { MAPPER.readValue<List<CodeScanRecommendation>>(it) }
        if (isProjectScope()) {
            LOG.debug { "Total code review issues returned from service: ${scanRecommendations.size}" }
        }
        return scanRecommendations.mapNotNull { recommendation ->
            val file = try {
                LocalFileSystem.getInstance().findFileByIoFile(
                    Path.of(sessionContext.sessionConfig.projectRoot.path, recommendation.filePath.substringAfter(sessionContext.project.name)).toFile()
                )
            } catch (e: Exception) {
                LOG.debug { "Cannot find file at location ${recommendation.filePath}" }
                null
            }

            if (file?.isDirectory == false) {
                runReadAction {
                    FileDocumentManager.getInstance().getDocument(file)
                }?.let { document ->
                    val endLineInDocument = minOf(maxOf(0, recommendation.endLine - 1), document.lineCount - 1)
                    val endCol = document.getLineEndOffset(endLineInDocument) - document.getLineStartOffset(endLineInDocument) + 1
                    val manager = CodeWhispererCodeScanManager.getInstance(project)
                    val isIssueIgnored = manager.isIgnoredIssue(recommendation.title, document, file, recommendation.startLine - 1)

                    CodeWhispererCodeScanIssue(
                        startLine = recommendation.startLine,
                        startCol = 1,
                        endLine = recommendation.endLine,
                        endCol = endCol,
                        file = file,
                        project = sessionContext.project,
                        title = recommendation.title,
                        description = recommendation.description,
                        detectorId = recommendation.detectorId,
                        detectorName = recommendation.detectorName,
                        findingId = recommendation.findingId,
                        ruleId = recommendation.ruleId,
                        relatedVulnerabilities = recommendation.relatedVulnerabilities,
                        severity = recommendation.severity,
                        recommendation = recommendation.remediation.recommendation,
                        suggestedFixes = recommendation.remediation.suggestedFixes,
                        codeSnippet = recommendation.codeSnippet,
                        isVisible = !isIssueIgnored,
                        autoDetected = isAutoScan(),
                        scanJobId = jobId
                    )
                }
            } else {
                null
            }
        }.filter {
            it.isVisible
        }.onEach { issue ->
            // Add range highlighters for all the issues found.
            runInEdt {
                issue.rangeHighlighter = issue.addRangeHighlighter()
            }
        }
    }

    private fun isProjectScope(): Boolean = sessionContext.codeAnalysisScope == CodeWhispererConstants.CodeAnalysisScope.PROJECT

    private fun isAutoScan(): Boolean =
        sessionContext.codeAnalysisScope == CodeWhispererConstants.CodeAnalysisScope.FILE && !sessionContext.sessionConfig.isInitiatedByChat()

    companion object {
        private val LOG = getLogger<CodeWhispererCodeScanSession>()
        private val MAPPER = jacksonObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        const val AWS_KMS = "aws:kms"
        const val APPLICATION_ZIP = "application/zip"
        const val CONTENT_MD5 = "Content-MD5"
        const val CONTENT_TYPE = "Content-Type"
        const val SERVER_SIDE_ENCRYPTION = "x-amz-server-side-encryption"
        const val SERVER_SIDE_ENCRYPTION_AWS_KMS_KEY_ID = "x-amz-server-side-encryption-aws-kms-key-id"
        const val SERVER_SIDE_ENCRYPTION_CONTEXT = "x-amz-server-side-encryption-context"
    }
}

sealed class CodeScanResponse {
    abstract val responseContext: CodeScanResponseContext

    data class Success(
        val issues: List<CodeWhispererCodeScanIssue>,
        override val responseContext: CodeScanResponseContext,
    ) : CodeScanResponse()

    data class Failure(
        // this needs some cleanup as it solely exists for tracking the job ID
        override val responseContext: CodeScanResponseContext,
        val failureReason: Throwable,
    ) : CodeScanResponse()
}

internal data class CodeScanRecommendation(
    val filePath: String,
    val startLine: Int,
    val endLine: Int,
    val title: String,
    val description: Description,
    val detectorId: String,
    val detectorName: String,
    val findingId: String,
    val ruleId: String?,
    val relatedVulnerabilities: List<String>,
    val severity: String,
    val remediation: Remediation,
    val codeSnippet: List<CodeLine>,
)

data class Description(val text: String, val markdown: String)

data class Remediation(val recommendation: Recommendation, val suggestedFixes: List<SuggestedFix>)

data class Recommendation(val text: String, val url: String?)

data class SuggestedFix(val description: String, val code: String, val codeFixJobId: String? = null, val references: MutableList<Reference> = mutableListOf())

data class CodeLine(val number: Int, val content: String)

data class CodeScanSessionContext(
    val project: Project,
    val sessionConfig: CodeScanSessionConfig,
    val codeAnalysisScope: CodeWhispererConstants.CodeAnalysisScope,
)

internal fun defaultPayloadContext() = PayloadContext(CodewhispererLanguage.Unknown, 0, 0, 0, listOf(), 0, 0)

internal fun defaultCreateUploadUrlServiceInvocationContext() = CreateUploadUrlServiceInvocationContext()

internal fun defaultCodeScanResponseContext() = CodeScanResponseContext(defaultPayloadContext(), defaultCreateUploadUrlServiceInvocationContext())
