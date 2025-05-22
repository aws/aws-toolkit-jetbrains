// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.codemodernizer.utils

import com.fasterxml.jackson.module.kotlin.readValue
import com.intellij.grazie.utils.orFalse
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.diff.impl.patch.PatchReader
import com.intellij.openapi.diff.impl.patch.formove.PatchApplier
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.module.JavaModuleType
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.util.io.FileUtil.createTempDirectory
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.serviceContainer.AlreadyDisposedException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import software.amazon.awssdk.awscore.exception.AwsServiceException
import software.amazon.awssdk.services.codewhispererruntime.model.AccessDeniedException
import software.amazon.awssdk.services.codewhispererruntime.model.CodeWhispererRuntimeException
import software.amazon.awssdk.services.codewhispererruntime.model.GetTransformationResponse
import software.amazon.awssdk.services.codewhispererruntime.model.InternalServerException
import software.amazon.awssdk.services.codewhispererruntime.model.ThrottlingException
import software.amazon.awssdk.services.codewhispererruntime.model.TransformationJob
import software.amazon.awssdk.services.codewhispererruntime.model.TransformationPlan
import software.amazon.awssdk.services.codewhispererruntime.model.TransformationProgressUpdate
import software.amazon.awssdk.services.codewhispererruntime.model.TransformationStatus
import software.amazon.awssdk.services.codewhispererruntime.model.TransformationStep
import software.amazon.awssdk.services.codewhispererruntime.model.TransformationUploadContext
import software.amazon.awssdk.services.codewhispererruntime.model.UploadContext
import software.amazon.awssdk.services.codewhispererruntime.model.ValidationException
import software.amazon.awssdk.services.ssooidc.model.InvalidGrantException
import software.aws.toolkits.core.utils.WaiterUnrecoverableException
import software.aws.toolkits.core.utils.Waiters.waitUntil
import software.aws.toolkits.core.utils.error
import software.aws.toolkits.core.utils.getLogger
import software.aws.toolkits.core.utils.info
import software.aws.toolkits.jetbrains.core.coroutines.EDT
import software.aws.toolkits.jetbrains.services.codemodernizer.CodeModernizerManager
import software.aws.toolkits.jetbrains.services.codemodernizer.CodeTransformTelemetryManager
import software.aws.toolkits.jetbrains.services.codemodernizer.client.GumbyClient
import software.aws.toolkits.jetbrains.services.codemodernizer.commands.CodeTransformMessageListener
import software.aws.toolkits.jetbrains.services.codemodernizer.constants.BILLING_RATE
import software.aws.toolkits.jetbrains.services.codemodernizer.constants.JOB_STATISTICS_TABLE_KEY
import software.aws.toolkits.jetbrains.services.codemodernizer.ideMaven.runClientSideBuild
import software.aws.toolkits.jetbrains.services.codemodernizer.model.CodeModernizerArtifact.Companion.MAPPER
import software.aws.toolkits.jetbrains.services.codemodernizer.model.CodeTransformType
import software.aws.toolkits.jetbrains.services.codemodernizer.model.JobId
import software.aws.toolkits.jetbrains.services.codemodernizer.model.PlanTable
import software.aws.toolkits.jetbrains.utils.notifyStickyWarn
import software.aws.toolkits.resources.message
import java.nio.file.Path
import java.nio.file.Paths
import java.time.Duration
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

data class PollingResult(
    val succeeded: Boolean,
    val jobDetails: TransformationJob?,
    val state: TransformationStatus,
    val transformationPlan: TransformationPlan?,
)

private const val IS_CLIENT_SIDE_BUILD_ENABLED = false

/**
 * Wrapper around [waitUntil] that polls the API DescribeMigrationJob to check the migration job status.
 */
suspend fun JobId.pollTransformationStatusAndPlan(
    transformType: CodeTransformType,
    succeedOn: Set<TransformationStatus>,
    failOn: Set<TransformationStatus>,
    clientAdaptor: GumbyClient,
    initialSleepDurationMillis: Long,
    sleepDurationMillis: Long,
    isDisposed: AtomicBoolean,
    project: Project,
    maxDuration: Duration = Duration.ofSeconds(604800),
    onStateChange: (previousStatus: TransformationStatus?, currentStatus: TransformationStatus, transformationPlan: TransformationPlan?) -> Unit,
): PollingResult {
    val telemetry = CodeTransformTelemetryManager.getInstance(project)
    var state = TransformationStatus.UNKNOWN_TO_SDK_VERSION
    var transformationResponse: GetTransformationResponse? = null
    var transformationPlan: TransformationPlan? = null
    var didSleepOnce = false
    val maxRefreshes = 10
    var numRefreshes = 0

    // refresh token at start of polling since local build just prior can take a long time
    refreshToken(project)

    try {
        waitUntil(
            succeedOn = { result -> result in succeedOn },
            failOn = { result -> result in failOn },
            maxDuration = maxDuration,
            exceptionsToStopOn = setOf(
                InternalServerException::class,
                ValidationException::class,
                AwsServiceException::class,
                CodeWhispererRuntimeException::class,
                RuntimeException::class,
            ),
            exceptionsToIgnore = setOf(ThrottlingException::class)
        ) {
            try {
                if (!didSleepOnce) {
                    delay(initialSleepDurationMillis)
                    didSleepOnce = true
                }
                if (isDisposed.get()) throw AlreadyDisposedException("The invoker is disposed.")
                transformationResponse = clientAdaptor.getCodeModernizationJob(this.id)
                val newStatus = transformationResponse?.transformationJob()?.status() ?: throw RuntimeException("Unable to get job status")
                var newPlan: TransformationPlan? = null
                if (newStatus in STATES_WHERE_PLAN_EXIST && transformType != CodeTransformType.SQL_CONVERSION) { // no plan for SQL conversions
                    delay(sleepDurationMillis)
                    newPlan = clientAdaptor.getCodeModernizationPlan(this).transformationPlan()
                }
                // TODO: remove flag when releasing CSB
                if (IS_CLIENT_SIDE_BUILD_ENABLED && newStatus == TransformationStatus.TRANSFORMING && newPlan != null) {
                    attemptLocalBuild(newPlan, this, project)
                }
                if (newStatus != state) {
                    telemetry.jobStatusChanged(this, newStatus.toString(), state.toString())
                }
                if (newPlan != transformationPlan) {
                    telemetry.jobStatusChanged(this, "PLAN_UPDATED", state.toString())
                }
                if (newStatus !in failOn && (newStatus != state || newPlan != transformationPlan)) {
                    transformationPlan = newPlan
                    onStateChange(state, newStatus, transformationPlan)
                }
                state = newStatus
                numRefreshes = 0
                return@waitUntil state
            } catch (e: AccessDeniedException) {
                if (numRefreshes++ > maxRefreshes) throw e
                refreshToken(project)
                return@waitUntil state
            } catch (e: InvalidGrantException) {
                CodeTransformMessageListener.instance.onReauthStarted()
                notifyStickyWarn(
                    message("codemodernizer.notification.warn.expired_credentials.title"),
                    message("codemodernizer.notification.warn.expired_credentials.content"),
                )
                return@waitUntil state
            } finally {
                delay(sleepDurationMillis)
            }
        }
    } catch (e: Exception) {
        // Still call onStateChange to update the UI
        onStateChange(state, TransformationStatus.FAILED, transformationPlan)
        when (e) {
            is WaiterUnrecoverableException, is AccessDeniedException, is InvalidGrantException -> {
                return PollingResult(false, transformationResponse?.transformationJob(), state, transformationPlan)
            }
            else -> throw e
        }
    }
    return PollingResult(true, transformationResponse?.transformationJob(), state, transformationPlan)
}

suspend fun attemptLocalBuild(plan: TransformationPlan, jobId: JobId, project: Project) {
    val artifactId = getClientInstructionArtifactId(plan)
    getLogger<CodeModernizerManager>().info { "Found artifactId: $artifactId" }
    if (artifactId != null) {
        val clientInstructionsPath = downloadClientInstructions(jobId, artifactId, project)
        getLogger<CodeModernizerManager>().info { "Downloaded client instructions for job ${jobId.id} and artifact $artifactId at: $clientInstructionsPath" }
        processClientInstructions(clientInstructionsPath, jobId, artifactId, project)
        getLogger<CodeModernizerManager>().info { "Finished processing client instructions for job ${jobId.id} and artifact $artifactId" }
    }
}

suspend fun processClientInstructions(clientInstructionsPath: Path, jobId: JobId, artifactId: String, project: Project) {
    var copyOfProjectSources = createTempDirectory("originalCopy_${jobId.id}_$artifactId", null).toPath()
    getLogger<CodeModernizerManager>().info { "About to copy the original project ZIP to: $copyOfProjectSources" }
    val originalProjectZip = CodeModernizerManager.getInstance(project).codeTransformationSession?.sessionContext?.originalUploadZipPath
    originalProjectZip?.let { unzipFile(it, copyOfProjectSources, isSqlMetadata = false, extractOnlySources = true) }
    copyOfProjectSources = copyOfProjectSources.resolve("sources") // where the user's source code is within the upload ZIP
    getLogger<CodeModernizerManager>().info { "Copied and unzipped original project sources to: $copyOfProjectSources" }

    val targetDir = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(copyOfProjectSources.toFile())
        ?: throw RuntimeException("Cannot find copy of project sources directory")

    withContext(EDT) {
        runWriteAction {
            // create temp module with project copy so that we can apply diff.patch
            val modifiableModel = ModuleManager.getInstance(project).getModifiableModel()
            val tempModule = modifiableModel.newModule(
                Paths.get(targetDir.path).resolve("temp.iml").toString(),
                JavaModuleType.getModuleType().id
            )

            try {
                val moduleModel = ModuleRootManager.getInstance(tempModule).modifiableModel
                moduleModel.addContentEntry(targetDir.url)
                moduleModel.commit()
                modifiableModel.commit()

                // apply diff.patch
                val patchReader = PatchReader(clientInstructionsPath)
                patchReader.parseAllPatches()
                PatchApplier(
                    project,
                    targetDir,
                    patchReader.allPatches,
                    null,
                    null
                ).execute()
                getLogger<CodeModernizerManager>().info { "Successfully applied patch file at $clientInstructionsPath" }

                val virtualFile = LocalFileSystem.getInstance().findFileByIoFile(clientInstructionsPath.toFile())
                    ?: throw RuntimeException("Cannot find patch file at $clientInstructionsPath")
                FileEditorManager.getInstance(project).openFile(virtualFile, true)
            } catch (e: Exception) {
                getLogger<CodeModernizerManager>().error {
                    "Error applying intermediate diff.patch for job ${jobId.id} and artifact $artifactId located at " +
                        "$clientInstructionsPath: $e"
                }
            } finally {
                runWriteAction {
                    ModuleManager.getInstance(project).disposeModule(tempModule)
                }
            }
        }
    }

    val (exitCode, buildOutput) = runClientSideBuild(targetDir, CodeModernizerManager.LOG, project)
    getLogger<CodeModernizerManager>().info { "Ran client-side build with an exit code of $exitCode" }
    val uploadZip = createClientSideBuildUploadZip(exitCode, buildOutput)
    getLogger<CodeModernizerManager>().info { "Created client-side build result upload zip for job ${jobId.id} and artifact $artifactId: ${uploadZip.path}" }
    val uploadContext = UploadContext.fromTransformationUploadContext(
        TransformationUploadContext.builder().jobId(jobId.id).uploadArtifactType("ClientBuildResult").build()
    )
    getLogger<CodeModernizerManager>().info { "About to call uploadPayload for job ${jobId.id} and artifact $artifactId" }
    try {
        CodeModernizerManager.getInstance(project).codeTransformationSession?.uploadPayload(uploadZip, uploadContext)
        getLogger<CodeModernizerManager>().info { "Upload succeeded; about to call ResumeTransformation for job ${jobId.id} and artifact $artifactId now" }
        CodeModernizerManager.getInstance(project).codeTransformationSession?.resumeTransformation()
    } finally {
        uploadZip.deleteRecursively()
        copyOfProjectSources.toFile().deleteRecursively()
        getLogger<CodeModernizerManager>().info { "Deleted copy of project sources and client-side build upload ZIP" }
    }
    // switch back to Transformation Hub view
    runInEdt {
        CodeModernizerManager.getInstance(project).getBottomToolWindow().show()
    }
}

suspend fun downloadClientInstructions(jobId: JobId, artifactId: String, project: Project): Path {
    val exportDestination = "downloadClientInstructions_${jobId.id}_$artifactId"
    val exportZipPath = createTempDirectory(exportDestination, null)
    val client = GumbyClient.getInstance(project)
    val downloadBytes = client.downloadExportResultArchive(jobId, artifactId)
    val downloadZipPath = zipToPath(downloadBytes, exportZipPath.toPath()).first.toAbsolutePath()
    unzipFile(downloadZipPath, exportZipPath.toPath())
    return exportZipPath.toPath().resolve("diff.patch")
}

fun getClientInstructionArtifactId(plan: TransformationPlan): String? {
    val steps = plan.transformationSteps().drop(1)
    val progressUpdate = findDownloadArtifactProgressUpdate(steps)
    val artifactId = progressUpdate?.downloadArtifacts()?.firstOrNull()?.downloadArtifactId()
    return artifactId
}

fun findDownloadArtifactProgressUpdate(transformationSteps: List<TransformationStep>) =
    transformationSteps
        .flatMap { it.progressUpdates().orEmpty() }
        .firstOrNull { update ->
            update.status().name == "AWAITING_CLIENT_ACTION" &&
                update.downloadArtifacts()?.firstOrNull()?.downloadArtifactId() != null
        }

// once dependency changes table (key of "1") available, plan is complete
fun isPlanComplete(plan: TransformationPlan?) = plan?.transformationSteps()?.get(0)?.progressUpdates()?.any { update -> update.name() == "1" }.orFalse()

// "name" holds the ID of the corresponding plan step (where table will go) and "description" holds the plan data
fun getTableMapping(stepZeroProgressUpdates: List<TransformationProgressUpdate>): Map<String, List<String>> =
    stepZeroProgressUpdates.groupBy(
        { it.name() },
        { it.description() }
    )

// ID of '0' reserved for job statistics table; only 1 table there
fun parseTableMapping(tableMapping: Map<String, List<String>>): PlanTable {
    val statsTable = tableMapping[JOB_STATISTICS_TABLE_KEY]?.get(0) ?: error("No transformation statistics table found in GetPlan response")
    return MAPPER.readValue<PlanTable>(statsTable)
}

// columns and name are shared between all PlanTables, so just combine the rows here
fun combineTableRows(tables: List<PlanTable>?): PlanTable? {
    if (tables == null) {
        return null
    }
    val combinedTable = PlanTable(tables.first().columns, mutableListOf(), tables.first().name)
    tables.forEach { table ->
        table.rows.forEach { row ->
            combinedTable.rows.add(row)
        }
    }
    return combinedTable
}

fun getBillingText(linesOfCode: Int): String {
    val estimatedCost = String.format(Locale.US, "%.2f", linesOfCode.times(BILLING_RATE))
    return message("codemodernizer.migration_plan.header.billing_text", linesOfCode, BILLING_RATE, estimatedCost)
}

fun getLinesOfCodeSubmitted(planTable: PlanTable) =
    planTable.rows.find { it.name == "linesOfCode" }?.value?.toInt()
