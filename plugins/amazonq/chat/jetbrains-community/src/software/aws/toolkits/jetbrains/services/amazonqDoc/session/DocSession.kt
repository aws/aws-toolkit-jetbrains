// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonqDoc.session

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtil
import software.amazon.awssdk.services.codewhispererruntime.model.DocInteractionType
import software.amazon.awssdk.services.codewhispererruntime.model.DocV2AcceptanceEvent
import software.amazon.awssdk.services.codewhispererruntime.model.DocV2GenerationEvent
import software.amazon.awssdk.services.codewhispererruntime.model.SendTelemetryEventResponse
import software.aws.toolkits.core.utils.debug
import software.aws.toolkits.core.utils.getLogger
import software.aws.toolkits.core.utils.warn
import software.aws.toolkits.jetbrains.common.clients.AmazonQCodeGenerateClient
import software.aws.toolkits.jetbrains.common.session.ConversationNotStartedState
import software.aws.toolkits.jetbrains.common.session.SessionState
import software.aws.toolkits.jetbrains.common.session.SessionStateConfigData
import software.aws.toolkits.jetbrains.common.util.AmazonQCodeGenService
import software.aws.toolkits.jetbrains.common.util.resolveAndCreateOrUpdateFile
import software.aws.toolkits.jetbrains.common.util.resolveAndDeleteFile
import software.aws.toolkits.jetbrains.services.amazonq.messages.MessagePublisher
import software.aws.toolkits.jetbrains.services.amazonqDoc.CODE_GENERATION_RETRY_LIMIT
import software.aws.toolkits.jetbrains.services.amazonqDoc.FEATURE_NAME
import software.aws.toolkits.jetbrains.services.amazonqDoc.MAX_PROJECT_SIZE_BYTES
import software.aws.toolkits.jetbrains.services.amazonqDoc.MetricDataOperationName
import software.aws.toolkits.jetbrains.services.amazonqDoc.MetricDataResult
import software.aws.toolkits.jetbrains.services.amazonqDoc.messages.sendAsyncEventProgress
import software.aws.toolkits.jetbrains.services.amazonqFeatureDev.CLIENT_ERROR_MESSAGES
import software.aws.toolkits.jetbrains.services.amazonqFeatureDev.ClientException
import software.aws.toolkits.jetbrains.services.amazonqFeatureDev.ConversationIdNotFoundException
import software.aws.toolkits.jetbrains.services.amazonqFeatureDev.LlmException
import software.aws.toolkits.jetbrains.services.amazonqFeatureDev.ServiceException
import software.aws.toolkits.jetbrains.services.amazonqFeatureDev.session.DeletedFileInfo
import software.aws.toolkits.jetbrains.services.amazonqFeatureDev.session.Interaction
import software.aws.toolkits.jetbrains.services.amazonqFeatureDev.session.NewFileZipInfo
import software.aws.toolkits.jetbrains.services.amazonqFeatureDev.session.SessionStateAction
import software.aws.toolkits.jetbrains.services.amazonqFeatureDev.util.CancellationTokenSource
import software.aws.toolkits.jetbrains.services.amazonqFeatureDev.util.getDiffMetrics
import software.aws.toolkits.jetbrains.services.codewhisperer.util.content
import java.nio.charset.Charset
import java.security.MessageDigest

private val logger = getLogger<AmazonQCodeGenerateClient>()

class DocSession(val tabID: String, val project: Project) {
    var context: DocSessionContext = DocSessionContext(project, MAX_PROJECT_SIZE_BYTES)
    val sessionStartTime = System.currentTimeMillis()

    var state: SessionState?
    var preloaderFinished: Boolean = false
    var localConversationId: String? = null
    var localLatestMessage: String = ""
    var task: String = ""
    val proxyClient: AmazonQCodeGenerateClient
    val amazonQCodeGenService: AmazonQCodeGenService
    private val _reportedChanges = mutableMapOf<String, String>()

    // retry session state vars
    private var codegenRetries: Int

    // Used to keep track of whether the current session/tab is currently authenticating/needs authenticating
    var isAuthenticating: Boolean

    init {
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
        filePaths.forEach { resolveAndCreateOrUpdateFile(context.addressableRoot.toNioPath(), it.zipFilePath, it.fileContent) }

        deletedFiles.forEach { resolveAndDeleteFile(context.addressableRoot.toNioPath(), it.zipFilePath) }

        // Taken from https://intellij-support.jetbrains.com/hc/en-us/community/posts/206118439-Refresh-after-external-changes-to-project-structure-and-sources
        VfsUtil.markDirtyAndRefresh(true, true, true, context.addressableRoot)
    }

    private fun getFromReportedChanges(filePath: NewFileZipInfo): String? {
        val key = getChangeIdentifier(filePath.zipFilePath)
        return this._reportedChanges[key]
    }

    private fun addToReportedChanges(filePath: NewFileZipInfo) {
        val key = getChangeIdentifier(filePath.zipFilePath)
        _reportedChanges[key] = filePath.fileContent
    }

    private fun getChangeIdentifier(filePath: String): String {
        val hash = MessageDigest.getInstance("SHA-1")
        hash.update(filePath.toByteArray(Charset.forName("UTF-8")))
        return hash.digest().joinToString("") { "%02x".format(it) }
    }

    data class AddedContent(
        val totalAddedChars: Int,
        val totalAddedLines: Int,
        val totalAddedFiles: Int,
    )

    fun countedGeneratedContent(filePaths: List<NewFileZipInfo>, interactionType: DocInteractionType? = null): AddedContent {
        var totalAddedChars = 0
        var totalAddedLines = 0
        var totalAddedFiles = 0

        filePaths.forEach { filePath ->
            val content = filePath.fileContent
            val reportedChange = getFromReportedChanges(filePath)
            if (interactionType == DocInteractionType.GENERATE_README) {
                if (reportedChange != null) {
                    val diffMetrics = getDiffMetrics(reportedChange, content)
                    totalAddedLines += diffMetrics.insertedLines
                    totalAddedChars += diffMetrics.insertedCharacters
                } else {
                    totalAddedChars += content.length
                    totalAddedLines += content.split('\n').size
                }
            } else {
                val sourceContent = reportedChange
                    ?: VfsUtil.findRelativeFile(filePath.zipFilePath, context.addressableRoot)?.content()
                        .orEmpty()
                val diffMetrics = getDiffMetrics(sourceContent, content)
                totalAddedLines += diffMetrics.insertedLines
                totalAddedChars += diffMetrics.insertedCharacters
            }
            addToReportedChanges(filePath)
            totalAddedFiles += 1
        }

        return AddedContent(
            totalAddedChars = totalAddedChars,
            totalAddedLines = totalAddedLines,
            totalAddedFiles = totalAddedFiles
        )
    }
    fun countAddedContent(filePaths: List<NewFileZipInfo>, interactionType: DocInteractionType? = null): AddedContent {
        var totalAddedChars = 0
        var totalAddedLines = 0
        var totalAddedFiles = 0

        filePaths.filter { !it.rejected }.forEach { filePath ->
            val content = filePath.fileContent
            if (interactionType == DocInteractionType.GENERATE_README) {
                totalAddedChars += content.length
                totalAddedLines += content.split('\n').size
            } else {
                val existingFileContent = VfsUtil.findRelativeFile(filePath.zipFilePath, context.addressableRoot)?.content()
                val diffMetrics = getDiffMetrics(existingFileContent.orEmpty(), content)
                totalAddedLines += diffMetrics.insertedLines
                totalAddedChars += diffMetrics.insertedCharacters
            }
            totalAddedFiles += 1
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
                throw ConversationIdNotFoundException(operation = "Session", desc = "Conversation ID not found")
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

    fun sendDocMetricData(operationName: MetricDataOperationName, result: MetricDataResult) {
        val sendFeatureDevTelemetryEventResponse: SendTelemetryEventResponse
        try {
            sendFeatureDevTelemetryEventResponse = proxyClient.sendDocMetricData(operationName.toString(), result.toString())
            val requestId = sendFeatureDevTelemetryEventResponse.responseMetadata().requestId()
            logger.debug {
                "${FEATURE_NAME}: succesfully sent doc metric data: OperationName: $operationName Result: $result RequestId: $requestId"
            }
        } catch (e: Exception) {
            logger.warn(e) { "${FEATURE_NAME}:failed to send doc metric data" }
        }
    }

    fun sendDocTelemetryEvent(
        generationEvent: DocV2GenerationEvent? = null,
        acceptanceEvent: DocV2AcceptanceEvent? = null,
    ) {
        val sendDocTelemetryEventResponse: SendTelemetryEventResponse
        try {
            sendDocTelemetryEventResponse = when {
                generationEvent != null -> proxyClient.sendDocTelemetryEvent(generationEvent, null)
                acceptanceEvent != null -> proxyClient.sendDocTelemetryEvent(null, acceptanceEvent)
                else -> {
                    logger.warn { "Neither generation nor acceptance event was provided" }
                    return
                }
            }
            val requestId = sendDocTelemetryEventResponse.responseMetadata().requestId()
            logger.debug {
                "${FEATURE_NAME}: succesfully sent doc telemetry: ConversationId: $conversationId RequestId: $requestId"
            }
        } catch (e: Exception) {
            logger.warn(e) { "${FEATURE_NAME}: failed to send doc telemetry" }
        }
    }

    fun getMetricResult(err: Exception): MetricDataResult {
        val metricDataResult: MetricDataResult
        when (err) {
            is ClientException,
            -> {
                metricDataResult = MetricDataResult.Error
            }

            is LlmException -> {
                metricDataResult = MetricDataResult.LlmFailure
            }

            is ServiceException -> {
                metricDataResult = MetricDataResult.Fault
            }

            else -> {
                val errorMessage = err.message.orEmpty()
                metricDataResult = if (CLIENT_ERROR_MESSAGES.any { errorMessage.contains(it) }) {
                    MetricDataResult.Error
                } else {
                    MetricDataResult.Fault
                }
            }
        }
        return metricDataResult
    }

    fun getUserIdentity(): String = proxyClient.connection().id
}
