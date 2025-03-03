// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonqDoc.session

import software.aws.toolkits.core.utils.getLogger
import software.aws.toolkits.core.utils.warn
import software.aws.toolkits.jetbrains.common.session.SessionState
import software.aws.toolkits.jetbrains.common.session.SessionStateConfig
import software.aws.toolkits.jetbrains.services.amazonq.messages.MessagePublisher
import software.aws.toolkits.jetbrains.services.amazonqDoc.FEATURE_NAME
import software.aws.toolkits.jetbrains.services.amazonqFeatureDev.session.CodeReferenceGenerated
import software.aws.toolkits.jetbrains.services.amazonqFeatureDev.session.DeletedFileInfo
import software.aws.toolkits.jetbrains.services.amazonqFeatureDev.session.NewFileZipInfo
import software.aws.toolkits.jetbrains.services.amazonqFeatureDev.session.SessionStateAction
import software.aws.toolkits.jetbrains.services.amazonqFeatureDev.session.SessionStatePhase
import software.aws.toolkits.jetbrains.services.amazonqFeatureDev.util.CancellationTokenSource
import software.aws.toolkits.jetbrains.services.amazonqFeatureDev.util.deleteUploadArtifact
import software.aws.toolkits.jetbrains.services.amazonqFeatureDev.util.uploadArtifactToS3

private val logger = getLogger<PrepareDocGenerationState>()

class PrepareDocGenerationState(
    override var tabID: String,
    override var approach: String,
    private var config: SessionStateConfig,
    val filePaths: List<NewFileZipInfo>,
    val deletedFiles: List<DeletedFileInfo>,
    val references: List<CodeReferenceGenerated>,
    var uploadId: String,
    private val currentIteration: Int,
    private var messenger: MessagePublisher,
    var codeGenerationRemainingIterationCount: Int? = null,
    var codeGenerationTotalIterationCount: Int? = null,
    override var token: CancellationTokenSource?,
) : SessionState {
    override val phase = SessionStatePhase.CODEGEN
    override suspend fun interact(action: SessionStateAction): SessionStateInteraction<SessionState> {
        var zipFileLength: Long? = null
        val nextState: SessionState
        try {
            val repoZipResult = config.repoContext.getProjectZip(false)
            val zipFileChecksum = repoZipResult.checksum
            zipFileLength = repoZipResult.contentLength
            val fileToUpload = repoZipResult.payload

            val uploadUrlResponse = config.amazonQCodeGenService.createUploadUrl(
                config.conversationId,
                zipFileChecksum,
                zipFileLength,
                uploadId,
                "docGeneration"
            )

            uploadArtifactToS3(
                uploadUrlResponse.uploadUrl(),
                fileToUpload,
                zipFileChecksum,
                zipFileLength,
                uploadUrlResponse.kmsKeyArn()
            )
            deleteUploadArtifact(fileToUpload)

            this.uploadId = uploadUrlResponse.uploadId()

            nextState = DocGenerationState(
                tabID = this.tabID,
                approach = "", // No approach needed,
                config = this.config,
                uploadId = this.uploadId,
                currentIteration = this.currentIteration,
                messenger = messenger,
                phase = phase,
                token = this.token
            )
        } catch (e: Exception) {
            logger.warn(e) { "$FEATURE_NAME: Code uploading failed: ${e.message}" }
            throw e
        }
        return nextState.interact(action)
    }
}
