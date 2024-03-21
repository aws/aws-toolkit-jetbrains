// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonqFeatureDev.session

import kotlinx.coroutines.delay
import software.amazon.awssdk.services.codewhispererruntime.model.CodeGenerationWorkflowStatus
import software.aws.toolkits.jetbrains.services.amazonq.messages.MessagePublisher
import software.aws.toolkits.jetbrains.services.amazonqFeatureDev.codeGenerationFailedError
import software.aws.toolkits.jetbrains.services.amazonqFeatureDev.messages.sendAnswerPart
import software.aws.toolkits.jetbrains.services.amazonqFeatureDev.util.exportTaskAssistArchiveResult
import software.aws.toolkits.jetbrains.services.amazonqFeatureDev.util.getTaskAssistCodeGeneration
import software.aws.toolkits.jetbrains.services.amazonqFeatureDev.util.startTaskAssistCodeGeneration
import software.aws.toolkits.resources.message
import software.aws.toolkits.telemetry.AmazonqTelemetry

class CodeGenerationState(
    override val tabID: String,
    override var approach: String,
    val config: SessionStateConfig,
    val uploadId: String,
    val currentIteration: Int,
    val repositorySize: Double,
    val messenger: MessagePublisher
) : SessionState {
    override val phase = SessionStatePhase.CODEGEN

    override suspend fun interact(action: SessionStateAction): SessionStateInteraction {
        val startTime = System.currentTimeMillis()

        val response = startTaskAssistCodeGeneration(
            proxyClient = config.proxyClient,
            conversationId = config.conversationId,
            uploadId = uploadId,
            message = action.msg
        )

        messenger.sendAnswerPart(
            tabId = tabID,
            message = message("amazonqFeatureDev.code_generation.generating_code")
        )

        val codeGenerationResult = generateCode(codeGenerationId = response.codeGenerationId())

        AmazonqTelemetry.codeGenerationInvoke(
            amazonqConversationId = config.conversationId,
            amazonqCodeGenerationResult = codeGenerationResult.status.toString(),
            amazonqGenerateCodeIteration = currentIteration.toDouble(),
            amazonqGenerateCodeResponseLatency = (System.currentTimeMillis() - startTime).toDouble(),
            amazonqRepositorySize = repositorySize,
            amazonqNumberOfFilesGenerated = if (codeGenerationResult is CodeGenerationComplete ) codeGenerationResult.newFiles.size.toDouble() else null,
            amazonqNumberOfReferences = if (codeGenerationResult is CodeGenerationComplete ) codeGenerationResult.references.size.toDouble() else null,
        )

        val nextState = PrepareCodeGenerationState(
            tabID = tabID,
            approach = approach,
            config = config,
            codeGenerationResult = codeGenerationResult,
            currentIteration = currentIteration + 1,
            uploadId = uploadId,
            messenger = messenger,
        )

        // It is not needed to interact right away with the code generation state.
        // returns a SessionStateInteraction object to be handled by the controller.
        return SessionStateInteraction(
            nextState = nextState,
            interaction = Interaction(content = "", interactionSucceeded = true)
        )
    }
}

private suspend fun CodeGenerationState.generateCode(codeGenerationId: String): CodeGenerationResult {
    val pollCount = 180
    val requestDelay = 10000L

    repeat(pollCount) {
        val codeGenerationResult = getTaskAssistCodeGeneration(
            proxyClient = config.proxyClient,
            conversationId = config.conversationId,
            codeGenerationId = codeGenerationId,
        )

        when (codeGenerationResult.codeGenerationStatus().status()) {
            CodeGenerationWorkflowStatus.IN_PROGRESS -> delay(requestDelay)
            CodeGenerationWorkflowStatus.COMPLETE -> {
                val codeGenerationStreamResult = exportTaskAssistArchiveResult(
                    proxyClient = config.proxyClient,
                    conversationId = config.conversationId
                )

                return CodeGenerationComplete(
                    newFiles = codeGenerationStreamResult.new_file_contents.map { NewFileZipInfo(it.key, it.value) },
                    deletedFiles = codeGenerationStreamResult.deleted_files,
                    references = codeGenerationStreamResult.references
                )
            }
            CodeGenerationWorkflowStatus.FAILED -> {
                if ( codeGenerationResult.codeGenerationStatusDetail().isNullOrEmpty() ) {
                    codeGenerationFailedError()
                }

                // Canned errors are not retryable, the error above on the contrary will let the user retry code generation.
                return CodeGenerationFailed(
                   message=codeGenerationResult.codeGenerationStatusDetail(),
                   retryable=false
                )
            }
            else -> error("Unknown status: ${codeGenerationResult.codeGenerationStatus().status()}")
        }
    }

    return CodeGenerationComplete(CodeGenerationWorkflowStatus.FAILED, emptyList(), emptyList(), emptyList())
}
