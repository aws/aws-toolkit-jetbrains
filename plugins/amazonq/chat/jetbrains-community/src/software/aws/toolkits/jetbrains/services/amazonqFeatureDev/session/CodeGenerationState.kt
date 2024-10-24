// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonqFeatureDev.session

import kotlinx.coroutines.delay
import software.amazon.awssdk.services.codewhispererruntime.model.CodeGenerationWorkflowStatus
import software.aws.toolkits.core.utils.getLogger
import software.aws.toolkits.core.utils.warn
import software.aws.toolkits.jetbrains.services.amazonq.messages.MessagePublisher
import software.aws.toolkits.jetbrains.services.amazonqFeatureDev.CodeGenerationException
import software.aws.toolkits.jetbrains.services.amazonqFeatureDev.EmptyPatchException
import software.aws.toolkits.jetbrains.services.amazonqFeatureDev.FEATURE_NAME
import software.aws.toolkits.jetbrains.services.amazonqFeatureDev.FeatureDevException
import software.aws.toolkits.jetbrains.services.amazonqFeatureDev.FeatureDevOperation
import software.aws.toolkits.jetbrains.services.amazonqFeatureDev.GuardrailsException
import software.aws.toolkits.jetbrains.services.amazonqFeatureDev.NoChangeRequiredException
import software.aws.toolkits.jetbrains.services.amazonqFeatureDev.PromptRefusalException
import software.aws.toolkits.jetbrains.services.amazonqFeatureDev.ThrottlingException
import software.aws.toolkits.jetbrains.services.amazonqFeatureDev.messages.sendAnswerPart
import software.aws.toolkits.jetbrains.services.amazonqFeatureDev.messages.sendUpdatePlaceholder
import software.aws.toolkits.jetbrains.services.amazonqFeatureDev.util.CancellationTokenSource
import software.aws.toolkits.jetbrains.services.cwc.controller.chat.telemetry.getStartUrl
import software.aws.toolkits.resources.message
import software.aws.toolkits.telemetry.AmazonqTelemetry
import software.aws.toolkits.telemetry.Result
import java.util.UUID

private val logger = getLogger<CodeGenerationState>()

class CodeGenerationState(
    override val tabID: String,
    override var approach: String,
    val config: SessionStateConfig,
    val uploadId: String,
    override var currentIteration: Int? = 0,
    val repositorySize: Double,
    val messenger: MessagePublisher,
    override var codeGenerationRemainingIterationCount: Int? = null,
    override var codeGenerationTotalIterationCount: Int? = null,
    var currentCodeGenerationId: String? = "EMPTY_CURRENT_CODE_GENERATION_ID",
    override var token: CancellationTokenSource?,
) : SessionState {
    override val phase = SessionStatePhase.CODEGEN

    override suspend fun interact(action: SessionStateAction): SessionStateInteraction {
        val startTime = System.currentTimeMillis()
        var result: Result = Result.Succeeded
        var failureReason: String? = null
        var failureReasonDesc: String? = null
        var codeGenerationWorkflowStatus: CodeGenerationWorkflowStatus = CodeGenerationWorkflowStatus.COMPLETE
        var numberOfReferencesGenerated: Int? = null
        var numberOfFilesGenerated: Int? = null
        try {
            val codeGenerationId = UUID.randomUUID()

            val response =
                config.featureDevService.startTaskAssistCodeGeneration(
                    conversationId = config.conversationId,
                    uploadId = uploadId,
                    message = action.msg,
                    codeGenerationId = codeGenerationId.toString(),
                    currentCodeGenerationId = currentCodeGenerationId.toString(),
                )

            if (action.token?.token?.isCancellationRequested() != true) {
                this.currentCodeGenerationId = codeGenerationId.toString()
            }

            messenger.sendAnswerPart(
                tabId = tabID,
                message = message("amazonqFeatureDev.code_generation.generating_code"),
            )
            messenger.sendUpdatePlaceholder(
                tabId = tabID,
                newPlaceholder = message("amazonqFeatureDev.code_generation.generating_code"),
            )
            val codeGenerationResult = generateCode(codeGenerationId = response.codeGenerationId(), messenger = messenger, token = action.token)
            numberOfReferencesGenerated = codeGenerationResult.references.size
            numberOfFilesGenerated = codeGenerationResult.newFiles.size
            codeGenerationRemainingIterationCount = codeGenerationResult.codeGenerationRemainingIterationCount
            codeGenerationTotalIterationCount = codeGenerationResult.codeGenerationTotalIterationCount

            val nextState =
                PrepareCodeGenerationState(
                    tabID = tabID,
                    approach = approach,
                    config = config,
                    filePaths = codeGenerationResult.newFiles,
                    deletedFiles = codeGenerationResult.deletedFiles,
                    references = codeGenerationResult.references,
                    currentIteration = currentIteration?.plus(1),
                    uploadId = uploadId,
                    messenger = messenger,
                    codeGenerationRemainingIterationCount = codeGenerationRemainingIterationCount,
                    codeGenerationTotalIterationCount = codeGenerationTotalIterationCount,
                    token = this.token,
                )

            // It is not needed to interact right away with the PrepareCodeGeneration.
            // returns therefore a SessionStateInteraction object to be handled by the controller.
            return SessionStateInteraction(
                nextState = nextState,
                interaction = Interaction(content = "", interactionSucceeded = true),
            )
        } catch (e: Exception) {
            logger.warn(e) { "$FEATURE_NAME: Code generation failed: ${e.message}" }
            result = Result.Failed
            failureReason = e.javaClass.simpleName
            if (e is FeatureDevException) {
                failureReason = e.reason()
                failureReasonDesc = e.reasonDesc()
            }
            codeGenerationWorkflowStatus = CodeGenerationWorkflowStatus.FAILED

            throw e
        } finally {
            currentIteration?.let {
                AmazonqTelemetry.codeGenerationInvoke(
                    amazonqConversationId = config.conversationId,
                    amazonqCodeGenerationResult = codeGenerationWorkflowStatus.toString(),
                    amazonqGenerateCodeIteration = it.toDouble(),
                    amazonqNumberOfReferences = numberOfReferencesGenerated?.toDouble(),
                    amazonqGenerateCodeResponseLatency = (System.currentTimeMillis() - startTime).toDouble(),
                    amazonqNumberOfFilesGenerated = numberOfFilesGenerated?.toDouble(),
                    amazonqRepositorySize = repositorySize,
                    result = result,
                    reason = failureReason,
                    reasonDesc = failureReasonDesc,
                    duration = (System.currentTimeMillis() - startTime).toDouble(),
                    credentialStartUrl = getStartUrl(config.featureDevService.project),
                )
            }
        }
    }
}

private suspend fun CodeGenerationState.generateCode(
    codeGenerationId: String,
    messenger: MessagePublisher,
    token: CancellationTokenSource?,
): CodeGenerationResult {
    val pollCount = 180
    val requestDelay = 10000L

    repeat(pollCount) {
        if (token?.token?.isCancellationRequested() == true) {
            return CodeGenerationResult(emptyList(), emptyList(), emptyList())
        }
        val codeGenerationResultState =
            config.featureDevService.getTaskAssistCodeGeneration(
                conversationId = config.conversationId,
                codeGenerationId = codeGenerationId,
            )

        when (codeGenerationResultState.codeGenerationStatus().status()) {
            CodeGenerationWorkflowStatus.COMPLETE -> {
                val codeGenerationStreamResult =
                    config.featureDevService.exportTaskAssistArchiveResult(
                        conversationId = config.conversationId,
                    )

                val newFileInfo = registerNewFiles(newFileContents = codeGenerationStreamResult.new_file_contents)
                val deletedFileInfo = registerDeletedFiles(deletedFiles = codeGenerationStreamResult.deleted_files)

                return CodeGenerationResult(
                    newFiles = newFileInfo,
                    deletedFiles = deletedFileInfo,
                    references = codeGenerationStreamResult.references,
                    codeGenerationRemainingIterationCount = codeGenerationResultState.codeGenerationRemainingIterationCount(),
                    codeGenerationTotalIterationCount = codeGenerationResultState.codeGenerationTotalIterationCount(),
                )
            }
            CodeGenerationWorkflowStatus.IN_PROGRESS -> {
                if (codeGenerationResultState.codeGenerationStatusDetail() != null) {
                    messenger.sendAnswerPart(
                        tabId = tabID,
                        message =
                        message("amazonqFeatureDev.code_generation.generating_code") +
                            "\n\n" + codeGenerationResultState.codeGenerationStatusDetail(),
                    )
                }
                delay(requestDelay)
            }
            CodeGenerationWorkflowStatus.FAILED -> {
                when (true) {
                    codeGenerationResultState.codeGenerationStatusDetail()?.contains(
                        "Guardrails",
                    ),
                    -> throw GuardrailsException(operation = FeatureDevOperation.GenerateCode.toString(), desc = "Failed guardrails")
                    codeGenerationResultState.codeGenerationStatusDetail()?.contains(
                        "PromptRefusal",
                    ),
                    -> throw PromptRefusalException(operation = FeatureDevOperation.GenerateCode.toString(), desc = "Prompt refusal")
                    codeGenerationResultState.codeGenerationStatusDetail()?.contains(
                        "EmptyPatch",
                    ),
                    -> {
                        if (codeGenerationResultState.codeGenerationStatusDetail().contains("NO_CHANGE_REQUIRED")) {
                            throw NoChangeRequiredException(operation = FeatureDevOperation.GenerateCode.toString(), desc = "No change required")
                        }
                        throw EmptyPatchException(operation = FeatureDevOperation.GenerateCode.toString(), desc = "Empty patch")
                    }
                    codeGenerationResultState.codeGenerationStatusDetail()?.contains(
                        "Throttling",
                    ),
                    -> throw ThrottlingException(operation = FeatureDevOperation.GenerateCode.toString(), desc = "Request throttled")
                    else -> throw CodeGenerationException(operation = FeatureDevOperation.GenerateCode.toString(), desc = null)
                }
            }
            else -> error("Unknown status: ${codeGenerationResultState.codeGenerationStatus().status()}")
        }
    }

    return CodeGenerationResult(emptyList(), emptyList(), emptyList())
}

fun registerNewFiles(newFileContents: Map<String, String>): List<NewFileZipInfo> =
    newFileContents.map {
        NewFileZipInfo(
            zipFilePath = it.key,
            fileContent = it.value,
            rejected = false,
        )
    }

fun registerDeletedFiles(deletedFiles: List<String>): List<DeletedFileInfo> =
    deletedFiles.map {
        DeletedFileInfo(
            zipFilePath = it,
            rejected = false,
        )
    }
