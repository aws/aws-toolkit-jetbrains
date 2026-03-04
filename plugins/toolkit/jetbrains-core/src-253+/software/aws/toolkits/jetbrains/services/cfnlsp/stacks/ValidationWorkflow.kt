// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cfnlsp.stacks

import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.project.Project
import software.aws.toolkits.jetbrains.services.cfnlsp.CfnClientService
import software.aws.toolkits.jetbrains.services.cfnlsp.protocol.CreateValidationParams
import software.aws.toolkits.jetbrains.services.cfnlsp.protocol.DescribeChangeSetParams
import software.aws.toolkits.jetbrains.services.cfnlsp.protocol.DescribeValidationStatusResult
import software.aws.toolkits.jetbrains.services.cfnlsp.protocol.Identifiable
import software.aws.toolkits.jetbrains.services.cfnlsp.protocol.StackActionPhase
import software.aws.toolkits.jetbrains.services.cfnlsp.protocol.StackActionState
import software.aws.toolkits.jetbrains.services.cfnlsp.protocol.StackChange
import software.aws.toolkits.jetbrains.services.cfnlsp.ui.ChangeSetDiffPanel
import software.aws.toolkits.jetbrains.utils.notifyError
import software.aws.toolkits.jetbrains.utils.notifyInfo
import software.aws.toolkits.resources.AwsToolkitBundle.message
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

internal sealed class ValidationResult {
    data class Success(
        val changes: List<StackChange>,
        val changeSetName: String,
        val details: DescribeValidationStatusResult,
    ) : ValidationResult()

    data class Failed(val reason: String?) : ValidationResult()
}

internal class ValidationWorkflow(
    private val project: Project,
    private val clientService: CfnClientService = CfnClientService.getInstance(project),
) {
    fun validate(params: CreateValidationParams): CompletableFuture<ValidationResult> {
        LastValidationService.getInstance(project).lastParams = params

        notifyInfo(
            title = message("cloudformation.validation.title"),
            content = message("cloudformation.validation.started", params.stackName),
            project = project
        )

        return clientService.createValidation(params).thenCompose { result ->
            if (result == null) {
                notifyError(
                    title = message("cloudformation.validation.title"),
                    content = message("cloudformation.validation.failed", params.stackName, "Failed to start validation"),
                    project = project
                )
                CompletableFuture.completedFuture(ValidationResult.Failed("Failed to start validation"))
            } else {
                StacksManager.getInstance(project).reload()
                ChangeSetsManager.getInstance(project).refreshChangeSets(params.stackName)
                pollForCompletion(result.id, result.changeSetName, params.stackName)
            }
        }.exceptionally { error ->
            notifyError(
                title = message("cloudformation.validation.title"),
                content = message("cloudformation.validation.failed", params.stackName, error.cause?.message ?: error.message ?: "Unknown error"),
                project = project
            )
            ValidationResult.Failed(error.message)
        }
    }

    private fun pollForCompletion(
        id: String,
        changeSetName: String,
        stackName: String,
    ): CompletableFuture<ValidationResult> {
        val future = CompletableFuture<ValidationResult>()
        val scheduler = Executors.newSingleThreadScheduledExecutor()

        scheduler.scheduleWithFixedDelay(
            {
                clientService.getValidationStatus(Identifiable(id))
                    .thenAccept { status ->
                        if (status == null) {
                            future.complete(ValidationResult.Failed("Failed to get validation status"))
                            scheduler.shutdown()
                            return@thenAccept
                        }

                        when (status.phase) {
                            StackActionPhase.VALIDATION_COMPLETE -> {
                                clientService.describeValidationStatus(Identifiable(id))
                                    .thenAccept { details ->
                                        if (details == null) {
                                            future.complete(ValidationResult.Failed("Failed to get validation details"))
                                        } else if (status.state == StackActionState.SUCCESSFUL) {
                                            notifyInfo(
                                                title = message("cloudformation.validation.title"),
                                                content = message("cloudformation.validation.success", stackName),
                                                project = project
                                            )
                                            // Fetch full change set details (includes property-level changes)
                                            clientService.describeChangeSet(
                                                DescribeChangeSetParams(changeSetName, stackName)
                                            ).thenAccept { changeSetResult ->
                                                val fullChanges = changeSetResult?.changes ?: status.changes ?: emptyList()
                                                runInEdt {
                                                    ChangeSetDiffPanel.show(
                                                        project = project,
                                                        stackName = stackName,
                                                        changeSetName = changeSetName,
                                                        changes = fullChanges,
                                                        enableDeploy = true,
                                                        status = changeSetResult?.status,
                                                        creationTime = changeSetResult?.creationTime,
                                                        description = changeSetResult?.description,
                                                    )
                                                }
                                            }
                                            future.complete(
                                                ValidationResult.Success(
                                                    changes = status.changes ?: emptyList(),
                                                    changeSetName = changeSetName,
                                                    details = details
                                                )
                                            )
                                        } else {
                                            notifyError(
                                                title = message("cloudformation.validation.title"),
                                                content = message("cloudformation.validation.failed", stackName, details.failureReason ?: "Unknown"),
                                                project = project
                                            )
                                            future.complete(ValidationResult.Failed(details.failureReason))
                                        }
                                        ChangeSetsManager.getInstance(project).refreshChangeSets(stackName)
                                        scheduler.shutdown()
                                    }
                            }

                            StackActionPhase.VALIDATION_FAILED -> {
                                clientService.describeValidationStatus(Identifiable(id))
                                    .thenAccept { details ->
                                        notifyError(
                                            title = message("cloudformation.validation.title"),
                                            content = message("cloudformation.validation.failed", stackName, details?.failureReason ?: "Unknown"),
                                            project = project
                                        )
                                        future.complete(ValidationResult.Failed(details?.failureReason))
                                        ChangeSetsManager.getInstance(project).refreshChangeSets(stackName)
                                        scheduler.shutdown()
                                    }
                            }

                            else -> {} // continue polling
                        }
                    }
                    .exceptionally { error ->
                        future.complete(ValidationResult.Failed(error.message))
                        scheduler.shutdown()
                        null
                    }
            },
            POLL_INTERVAL_MS,
            POLL_INTERVAL_MS,
            TimeUnit.MILLISECONDS
        )

        return future
    }

    companion object {
        private const val POLL_INTERVAL_MS = 1000L
    }
}
