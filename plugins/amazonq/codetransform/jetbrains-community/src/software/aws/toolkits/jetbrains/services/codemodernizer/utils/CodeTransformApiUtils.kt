// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.codemodernizer.utils

import com.intellij.openapi.project.Project
import com.intellij.serviceContainer.AlreadyDisposedException
import software.amazon.awssdk.awscore.exception.AwsServiceException
import software.amazon.awssdk.services.codewhispererruntime.model.AccessDeniedException
import software.amazon.awssdk.services.codewhispererruntime.model.CodeWhispererRuntimeException
import software.amazon.awssdk.services.codewhispererruntime.model.GetTransformationResponse
import software.amazon.awssdk.services.codewhispererruntime.model.InternalServerException
import software.amazon.awssdk.services.codewhispererruntime.model.ThrottlingException
import software.amazon.awssdk.services.codewhispererruntime.model.TransformationJob
import software.amazon.awssdk.services.codewhispererruntime.model.TransformationPlan
import software.amazon.awssdk.services.codewhispererruntime.model.TransformationStatus
import software.amazon.awssdk.services.codewhispererruntime.model.TransformationStep
import software.amazon.awssdk.services.codewhispererruntime.model.ValidationException
import software.aws.toolkits.core.utils.WaiterUnrecoverableException
import software.aws.toolkits.core.utils.Waiters.waitUntil
import software.aws.toolkits.jetbrains.services.codemodernizer.CodeTransformTelemetryManager
import software.aws.toolkits.jetbrains.services.codemodernizer.client.GumbyClient
import software.aws.toolkits.jetbrains.services.codemodernizer.model.JobId
import java.lang.Thread.sleep
import java.time.Duration
import java.time.Instant
import java.util.concurrent.atomic.AtomicBoolean

data class PollingResult(
    val succeeded: Boolean,
    val jobDetails: TransformationJob?,
    val state: TransformationStatus,
    val transformationPlan: TransformationPlan?
)

/**
 * Wrapper around [waitUntil] that polls the API DescribeMigrationJob to check the migration job status.
 */
suspend fun JobId.pollTransformationStatusAndPlan(
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
                    sleep(initialSleepDurationMillis)
                    didSleepOnce = true
                }
                if (isDisposed.get()) throw AlreadyDisposedException("The invoker is disposed.")
                transformationResponse = clientAdaptor.getCodeModernizationJob(this.id)
                val newStatus = transformationResponse?.transformationJob()?.status() ?: throw RuntimeException("Unable to get job status")
                var newPlan: TransformationPlan? = null
                if (newStatus in STATES_WHERE_PLAN_EXIST) {
                    sleep(sleepDurationMillis)
                    newPlan = clientAdaptor.getCodeModernizationPlan(this).transformationPlan()
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
            } finally {
                sleep(sleepDurationMillis)
            }
        }
    } catch (e: Exception) {
        // Still call onStateChange to update the UI
        onStateChange(state, TransformationStatus.FAILED, transformationPlan)
        when (e) {
            is WaiterUnrecoverableException, is AccessDeniedException -> {
                return PollingResult(false, transformationResponse?.transformationJob(), state, transformationPlan)
            }
            else -> throw e
        }
    }
    return PollingResult(true, transformationResponse?.transformationJob(), state, transformationPlan)
}

fun getTransformationStepsFixture(
    jobId: JobId
): List<TransformationStep> {
    println("In getTransformationStepsFixture $jobId")
    val transformationStepBuilder = TransformationStep.builder()
        .id("fake-step-id-1")
        .name("Building Code")
        .description("Building dependencies")
        .status(TransformationStatus.PAUSED.toString())
        .startTime(Instant.now())
        .endTime(Instant.now())

//    progressUpdates = listOf(
//        name = "Status step",
//        status = TransformationStepStatus.FAILED,
//        description = "This step should be hil identifier",
//        startTime = Date(),
//        endTime = Date()
//    ),
//            downloadArtifacts = listof(
//
//            ),

    return listOf(transformationStepBuilder.build())
}

fun getArtifactIdentifiers(transformationSteps: List<TransformationStep>): Array<String> {
    println("In getArtifactIdentifiers $transformationSteps")
    return arrayOf("fake-artifact-id", "fake-artifact-type")
}

fun downloadResultArchive(jobId: JobId, artifactId: String, artifactType: String): Array<String> {
    println("In downloadResultArchive $jobId $artifactId $artifactType")
    return arrayOf("pomFileVirtualRef", "manifestFileVirtualRef")
}
