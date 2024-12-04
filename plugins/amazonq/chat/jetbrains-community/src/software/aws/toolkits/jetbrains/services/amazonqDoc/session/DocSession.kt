// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonqDoc.session

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtil
import software.amazon.awssdk.services.codewhispererruntime.model.DocGenerationEvent
import software.amazon.awssdk.services.codewhispererruntime.model.DocGenerationInteractionType
import software.amazon.awssdk.services.codewhispererruntime.model.SendTelemetryEventResponse
import software.aws.toolkits.core.utils.debug
import software.aws.toolkits.core.utils.getLogger
import software.aws.toolkits.core.utils.warn
import software.aws.toolkits.jetbrains.common.clients.AmazonQCodeGenerateClient
import software.aws.toolkits.jetbrains.common.session.ConversationNotStartedState
import software.aws.toolkits.jetbrains.common.session.SessionState
import software.aws.toolkits.jetbrains.common.session.SessionStateConfigData
import software.aws.toolkits.jetbrains.common.util.AmazonQCodeGenService
import software.aws.toolkits.jetbrains.common.util.getDiffCharsAndLines
import software.aws.toolkits.jetbrains.common.util.resolveAndCreateOrUpdateFile
import software.aws.toolkits.jetbrains.common.util.resolveAndDeleteFile
import software.aws.toolkits.jetbrains.services.amazonq.FeatureDevSessionContext
import software.aws.toolkits.jetbrains.services.amazonq.messages.MessagePublisher
import software.aws.toolkits.jetbrains.services.amazonqDoc.CODE_GENERATION_RETRY_LIMIT
import software.aws.toolkits.jetbrains.services.amazonqDoc.FEATURE_NAME
import software.aws.toolkits.jetbrains.services.amazonqDoc.MAX_PROJECT_SIZE_BYTES
import software.aws.toolkits.jetbrains.services.amazonqDoc.conversationIdNotFound
import software.aws.toolkits.jetbrains.services.amazonqDoc.messages.sendAsyncEventProgress
import software.aws.toolkits.jetbrains.services.amazonqFeatureDev.session.DeletedFileInfo
import software.aws.toolkits.jetbrains.services.amazonqFeatureDev.session.Interaction
import software.aws.toolkits.jetbrains.services.amazonqFeatureDev.session.NewFileZipInfo
import software.aws.toolkits.jetbrains.services.amazonqFeatureDev.session.SessionStateAction
import software.aws.toolkits.jetbrains.services.amazonqFeatureDev.util.CancellationTokenSource
import software.aws.toolkits.jetbrains.services.codewhisperer.util.content

private val logger = getLogger<AmazonQCodeGenerateClient>()

class DocSession(val tabID: String, val project: Project) {
    var context: FeatureDevSessionContext
    val sessionStartTime = System.currentTimeMillis()

    var state: SessionState?
    var preloaderFinished: Boolean = false
    var localConversationId: String? = null
    var localLatestMessage: String = ""
    var task: String = ""
    val proxyClient: AmazonQCodeGenerateClient
    val amazonQCodeGenService: AmazonQCodeGenService

    // retry session state vars
    private var codegenRetries: Int

    // Used to keep track of whether the current session/tab is currently authenticating/needs authenticating
    var isAuthenticating: Boolean

    init {
        context = FeatureDevSessionContext(project, MAX_PROJECT_SIZE_BYTES)
        proxyClient = AmazonQCodeGenerateClient.getInstance(project)
        amazonQCodeGenService = AmazonQCodeGenService(proxyClient, project)
        state = ConversationNotStartedState("", tabID, token = null)
        isAuthenticating = false
        codegenRetries = CODE_GENERATION_RETRY_LIMIT
    }

    fun conversationIDLog(conversationId: String) = "$FEATURE_NAME Conversation ID: $conversationId"

    /**
     * Preload any events that have to run before a chat message can be sent
     */
    suspend fun preloader(msg: String, messenger: MessagePublisher) {
        if (!preloaderFinished) {
            setupConversation(msg, messenger)
            preloaderFinished = true
            messenger.sendAsyncEventProgress(tabId = this.tabID, inProgress = true)
        }
    }

    /**
     * Starts a conversation with the backend and uploads the repo for the LLMs to be able to use it.
     */
    fun setupConversation(msg: String, messenger: MessagePublisher) {
        // Store the initial message when setting up the conversation so that if it fails we can retry with this message
        localLatestMessage = msg

        localConversationId = amazonQCodeGenService.createConversation()
        logger<DocSession>().info(conversationIDLog(this.conversationId))

        val sessionStateConfig = getSessionStateConfig().copy(conversationId = this.conversationId)
        state = PrepareDocGenerationState(
            tabID = sessionState.tabID,
            approach = sessionState.approach,
            config = sessionStateConfig,
            filePaths = emptyList(),
            deletedFiles = emptyList(),
            references = emptyList(),
            currentIteration = 0, // first code gen iteration
            uploadId = "", // There is no code gen uploadId so far
            messenger = messenger,
            token = CancellationTokenSource()
        )
    }

    /**
     * Triggered by the Insert code follow-up button to apply code changes.
     */
    fun insertChanges(filePaths: List<NewFileZipInfo>, deletedFiles: List<DeletedFileInfo>) {
        val selectedSourceFolder = context.selectedSourceFolder.toNioPath()

        filePaths.forEach { resolveAndCreateOrUpdateFile(selectedSourceFolder, it.zipFilePath, it.fileContent) }

        deletedFiles.forEach { resolveAndDeleteFile(selectedSourceFolder, it.zipFilePath) }

        // Taken from https://intellij-support.jetbrains.com/hc/en-us/community/posts/206118439-Refresh-after-external-changes-to-project-structure-and-sources
        VfsUtil.markDirtyAndRefresh(true, true, true, context.selectedSourceFolder)
    }

    data class AddedContent(
        val totalAddedChars: Int,
        val totalAddedLines: Int,
        val totalAddedFiles: Int,
    )

    fun countAddedContent(filePaths: List<NewFileZipInfo>, interactionType: DocGenerationInteractionType? = null): AddedContent {
        var totalAddedChars = 0
        var totalAddedLines = 0
        var totalAddedFiles = 0

        filePaths.filter { !it.rejected }.forEach { filePath ->
            val existingFile = VfsUtil.findRelativeFile(filePath.zipFilePath, context.selectedSourceFolder)
            val content = filePath.fileContent
            totalAddedFiles += 1

            if (existingFile != null && interactionType == DocGenerationInteractionType.UPDATE_README) {
                val existingContent = existingFile.content()
                val (addedChars, addedLines) = getDiffCharsAndLines(existingContent, content)
                totalAddedChars += addedChars
                totalAddedLines += addedLines
            } else {
                totalAddedChars += content.length
                totalAddedLines += content.split('\n').size
            }
        }

        return AddedContent(
            totalAddedChars = totalAddedChars,
            totalAddedLines = totalAddedLines,
            totalAddedFiles = totalAddedFiles
        )
    }

    suspend fun send(msg: String): Interaction {
        // When the task/"thing to do" hasn't been set yet, we want it to be the incoming message
        if (task.isEmpty() && msg.isNotEmpty()) {
            task = msg
        }

        localLatestMessage = msg

        return nextInteraction(msg)
    }

    private suspend fun nextInteraction(msg: String): Interaction {
        var action = SessionStateAction(
            task = task,
            msg = msg,
        )

        val resp = sessionState.interact(action)
        if (resp.nextState != null) {
            // Approach may have been changed after the interaction
            val newApproach = sessionState.approach

            // Move to the next state
            state = resp.nextState

            // If approach was changed then we need to set it in the next state and this state
            sessionState.approach = newApproach
        }

        return resp.interaction
    }

    fun getSessionStateConfig(): SessionStateConfigData = SessionStateConfigData(
        conversationId = this.conversationId,
        repoContext = this.context,
        amazonQCodeGenService = this.amazonQCodeGenService,
    )

    val conversationId: String
        get() {
            if (localConversationId == null) {
                conversationIdNotFound()
            } else {
                return localConversationId as String
            }
        }

    val conversationIdUnsafe: String?
        get() = localConversationId

    val sessionState: SessionState
        get() {
            if (state == null) {
                throw Error("State should be initialized before it's read")
            } else {
                return state as SessionState
            }
        }

    val latestMessage: String
        get() = this.localLatestMessage

    val retries: Int
        get() = codegenRetries

    fun decreaseRetries() {
        codegenRetries -= 1
    }

    fun sendDocGenerationEvent(docGenerationEvent: DocGenerationEvent) {
        val sendDocGenerationEventResponse: SendTelemetryEventResponse
        try {
            sendDocGenerationEventResponse = proxyClient.sendDocGenerationTelemetryEvent(docGenerationEvent)
            val requestId = sendDocGenerationEventResponse.responseMetadata().requestId()
            logger.debug {
                "${FEATURE_NAME}: succesfully sent doc generation telemetry: ConversationId: $conversationId RequestId: $requestId"
            }
        } catch (e: Exception) {
            logger.warn(e) { "${FEATURE_NAME}: failed to send doc generation telemetry" }
        }
    }

    fun getUserIdentity(): String = proxyClient.connection().id
}
