// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonqDoc.session

import kotlinx.coroutines.delay
import software.amazon.awssdk.services.codewhispererruntime.model.CodeGenerationWorkflowStatus
import software.aws.toolkits.core.utils.getLogger
import software.aws.toolkits.core.utils.warn
import software.aws.toolkits.jetbrains.common.session.Intent
import software.aws.toolkits.jetbrains.common.session.SessionState
import software.aws.toolkits.jetbrains.common.session.SessionStateConfig
import software.aws.toolkits.jetbrains.services.amazonq.messages.MessagePublisher
import software.aws.toolkits.jetbrains.services.amazonqDoc.ContentLengthException
import software.aws.toolkits.jetbrains.services.amazonqDoc.DocGenerationException
import software.aws.toolkits.jetbrains.services.amazonqDoc.EmptyPatchException
import software.aws.toolkits.jetbrains.services.amazonqDoc.FEATURE_NAME
import software.aws.toolkits.jetbrains.services.amazonqDoc.GuardrailsException
import software.aws.toolkits.jetbrains.services.amazonqDoc.NoChangeRequiredException
import software.aws.toolkits.jetbrains.services.amazonqDoc.PromptRefusalException
import software.aws.toolkits.jetbrains.services.amazonqDoc.PromptTooVagueException
import software.aws.toolkits.jetbrains.services.amazonqDoc.PromptUnrelatedException
import software.aws.toolkits.jetbrains.services.amazonqDoc.ReadmeTooLargeException
import software.aws.toolkits.jetbrains.services.amazonqDoc.ReadmeUpdateTooLargeException
import software.aws.toolkits.jetbrains.services.amazonqDoc.ThrottlingException
import software.aws.toolkits.jetbrains.services.amazonqDoc.WorkspaceEmptyException
import software.aws.toolkits.jetbrains.services.amazonqDoc.controller.DocGenerationStep
import software.aws.toolkits.jetbrains.services.amazonqDoc.controller.Mode
import software.aws.toolkits.jetbrains.services.amazonqDoc.controller.docGenerationProgressMessage
import software.aws.toolkits.jetbrains.services.amazonqDoc.inProgress
import software.aws.toolkits.jetbrains.services.amazonqDoc.messages.sendAnswerPart
import software.aws.toolkits.jetbrains.services.amazonqDoc.messages.sendUpdatePromptProgress
import software.aws.toolkits.jetbrains.services.amazonqFeatureDev.FeatureDevOperation
import software.aws.toolkits.jetbrains.services.amazonqFeatureDev.session.CodeGenerationResult
import software.aws.toolkits.jetbrains.services.amazonqFeatureDev.session.Interaction
import software.aws.toolkits.jetbrains.services.amazonqFeatureDev.session.SessionStateAction
import software.aws.toolkits.jetbrains.services.amazonqFeatureDev.session.SessionStatePhase
import software.aws.toolkits.jetbrains.services.amazonqFeatureDev.session.registerDeletedFiles
import software.aws.toolkits.jetbrains.services.amazonqFeatureDev.session.registerNewFiles
import software.aws.toolkits.jetbrains.services.amazonqFeatureDev.util.CancellationTokenSource
import software.aws.toolkits.resources.message

private val logger = getLogger<DocGenerationState>()

class DocGenerationState(
    override val tabID: String,
    override var approach: String,
    val config: SessionStateConfig,
    val uploadId: String,
    val currentIteration: Int,
    val messenger: MessagePublisher,
    var codeGenerationRemainingIterationCount: Int? = null,
    var codeGenerationTotalIterationCount: Int? = null,
    override val phase: SessionStatePhase?,
    override var token: CancellationTokenSource?,
) : SessionState {
    override suspend fun interact(action: SessionStateAction): SessionStateInteraction<SessionState> {
        try {
            val response = config.amazonQCodeGenService.startTaskAssistCodeGeneration(
                conversationId = config.conversationId,
                uploadId = uploadId,
                message = action.msg,
                intent = Intent.DOC
            )
            val mode = if (action.msg == message("amazonqDoc.session.create")) Mode.CREATE else null
            val codeGenerationResult = generateCode(codeGenerationId = response.codeGenerationId(), mode, token)
            codeGenerationRemainingIterationCount = codeGenerationResult.codeGenerationRemainingIterationCount
            codeGenerationTotalIterationCount = codeGenerationResult.codeGenerationTotalIterationCount

            val nextState = PrepareDocGenerationState(
                tabID = tabID,
                approach = approach,
                config = config,
                filePaths = codeGenerationResult.newFiles,
                deletedFiles = codeGenerationResult.deletedFiles,
                references = codeGenerationResult.references,
                currentIteration = currentIteration + 1,
                uploadId = uploadId,
                messenger = messenger,
                codeGenerationRemainingIterationCount = codeGenerationRemainingIterationCount,
                codeGenerationTotalIterationCount = codeGenerationTotalIterationCount,
                token = token
            )

            // It is not needed to interact right away with the PrepareCodeGeneration.
            // returns therefore a SessionStateInteraction object to be handled by the controller.
            return SessionStateInteraction(
                nextState = nextState,
                interaction = Interaction(content = "", interactionSucceeded = true)
            )
        } catch (e: Exception) {
            logger.warn(e) { "$FEATURE_NAME: Code generation failed: ${e.message}" }
            throw e
        }
    }
}

fun getFileSummaryPercentage(input: String): Double {
    // Split the input string by newline characters
    val lines = input.split("\n")

    // Find the line containing "summarized:"
    val summaryLine = lines.find { it.contains("summarized:") }

    // If the line is not found, return -1.0
    if (summaryLine == null) {
        return -1.0
    }

    // Extract the numbers from the summary line
    val (summarized, total) = summaryLine.split(":")[1].trim().split(" of ").map { it.toDouble() }

    // Calculate the percentage
    val percentage = (summarized / total) * 100

    return percentage
}

private suspend fun DocGenerationState.generateCode(codeGenerationId: String, mode: Mode?, token: CancellationTokenSource?): CodeGenerationResult {
    val pollCount = 180
    val requestDelay = 10000L

    repeat(pollCount) {
        if (token?.token?.isCancellationRequested() == true) {
            return CodeGenerationResult(emptyList(), emptyList(), emptyList())
        }

        val codeGenerationResultState = config.amazonQCodeGenService.getTaskAssistCodeGeneration(
            conversationId = config.conversationId,
            codeGenerationId = codeGenerationId,
        )

        when (codeGenerationResultState.codeGenerationStatus().status()) {
            CodeGenerationWorkflowStatus.COMPLETE -> {
                val codeGenerationStreamResult = config.amazonQCodeGenService.exportTaskAssistArchiveResult(
                    conversationId = config.conversationId
                )

                val newFileInfo = registerNewFiles(newFileContents = codeGenerationStreamResult.newFileContents)
                val deletedFileInfo = registerDeletedFiles(deletedFiles = codeGenerationStreamResult.deletedFiles.orEmpty())

                messenger.sendUpdatePromptProgress(tabId = tabID, progressField = null)

                return CodeGenerationResult(
                    newFiles = newFileInfo,
                    deletedFiles = deletedFileInfo,
                    references = codeGenerationStreamResult.references,
                    codeGenerationRemainingIterationCount = codeGenerationResultState.codeGenerationRemainingIterationCount(),
                    codeGenerationTotalIterationCount = codeGenerationResultState.codeGenerationTotalIterationCount()
                )
            }

            CodeGenerationWorkflowStatus.IN_PROGRESS -> {
                if (codeGenerationResultState.codeGenerationStatusDetail() != null) {
                    val progress = getFileSummaryPercentage(codeGenerationResultState.codeGenerationStatusDetail())

                    messenger.sendUpdatePromptProgress(
                        tabID,
                        inProgress(
                            progress.toInt(),
                            message(if (progress >= 100) "amazonqDoc.inprogress_message.generating" else "amazonqDoc.progress_message.summarizing")
                        )
                    )

                    messenger.sendAnswerPart(
                        tabId = tabID,
                        message = docGenerationProgressMessage(
                            if (progress < 100) {
                                if (progress < 20) {
                                    DocGenerationStep.CREATE_KNOWLEDGE_GRAPH
                                } else {
                                    DocGenerationStep.SUMMARIZING_FILES
                                }
                            } else {
                                DocGenerationStep.GENERATING_ARTIFACTS
                            },
                            mode
                        )
                    )
                }

                delay(requestDelay)
            }

            CodeGenerationWorkflowStatus.FAILED -> {
                messenger.sendUpdatePromptProgress(tabId = tabID, progressField = null)
                val remainingIterations = codeGenerationResultState.codeGenerationRemainingIterationCount()

                when (true) {
                    codeGenerationResultState.codeGenerationStatusDetail()?.contains(
                        "README_TOO_LARGE"
                    ),
                    -> throw ReadmeTooLargeException(
                        operation = FeatureDevOperation.GenerateCode.toString(),
                        desc = "Readme too large",
                        remainingIterations = remainingIterations
                    )

                    codeGenerationResultState.codeGenerationStatusDetail()?.contains(
                        "README_UPDATE_TOO_LARGE"
                    ),
                    -> throw ReadmeUpdateTooLargeException(
                        operation = FeatureDevOperation.GenerateCode.toString(),
                        desc = "Readme update too large",
                        remainingIterations = remainingIterations
                    )

                    codeGenerationResultState.codeGenerationStatusDetail()?.contains(
                        "WORKSPACE_TOO_LARGE"
                    ),
                    -> throw ContentLengthException(
                        operation = FeatureDevOperation.GenerateCode.toString(),
                        desc = "Workspace too large"
                    )

                    codeGenerationResultState.codeGenerationStatusDetail()?.contains(
                        "WORKSPACE_EMPTY"
                    ),
                    -> throw WorkspaceEmptyException(
                        operation = FeatureDevOperation.GenerateCode.toString(),
                        desc = "Workspace empty"
                    )

                    codeGenerationResultState.codeGenerationStatusDetail()?.contains(
                        "PROMPT_UNRELATED"
                    ),
                    -> throw PromptUnrelatedException(
                        operation = FeatureDevOperation.GenerateCode.toString(),
                        desc = "Prompt unrelated",
                        remainingIterations = remainingIterations
                    )

                    codeGenerationResultState.codeGenerationStatusDetail()?.contains(
                        "PROMPT_TOO_VAGUE"
                    ),
                    -> throw PromptTooVagueException(
                        operation = FeatureDevOperation.GenerateCode.toString(),
                        desc = "Prompt too vague",
                        remainingIterations = remainingIterations
                    )

                    codeGenerationResultState.codeGenerationStatusDetail()?.contains(
                        "PromptRefusal"
                    ),
                    -> throw PromptRefusalException(
                        operation = FeatureDevOperation.GenerateCode.toString(),
                        desc = "Prompt refusal",
                        remainingIterations = remainingIterations
                    )

                    codeGenerationResultState.codeGenerationStatusDetail()?.contains(
                        "Guardrails"
                    ),
                    -> throw GuardrailsException(
                        operation = FeatureDevOperation.GenerateCode.toString(),
                        desc = "Guardrails violation"
                    )

                    codeGenerationResultState.codeGenerationStatusDetail()?.contains(
                        "EmptyPatch"
                    ),
                    -> {
                        if (codeGenerationResultState.codeGenerationStatusDetail()?.contains("NO_CHANGE_REQUIRED") == true) {
                            throw NoChangeRequiredException(
                                operation = FeatureDevOperation.GenerateCode.toString(),
                                desc = "No change required"
                            )
                        }
                        throw EmptyPatchException(
                            operation = FeatureDevOperation.GenerateCode.toString(),
                            desc = "Empty patch"
                        )
                    }

                    codeGenerationResultState.codeGenerationStatusDetail()?.contains(
                        "Throttling"
                    ),
                    -> throw ThrottlingException(
                        operation = FeatureDevOperation.GenerateCode.toString(),
                        desc = "Throttling occurred"
                    )

                    else -> throw DocGenerationException(operation = FeatureDevOperation.GenerateCode.toString(), desc = null)
                }
            }

            else -> error("Unknown status: ${codeGenerationResultState.codeGenerationStatus().status()}")
        }
    }

    return CodeGenerationResult(emptyList(), emptyList(), emptyList())
}
