// Copyright 2023 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonqFeatureDev.clients

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import kotlinx.coroutines.future.await
import software.amazon.awssdk.services.codewhispererruntime.CodeWhispererRuntimeClient
import software.amazon.awssdk.services.codewhispererruntime.model.ArtifactType
import software.amazon.awssdk.services.codewhispererruntime.model.ContentChecksumType
import software.amazon.awssdk.services.codewhispererruntime.model.CreateTaskAssistConversationRequest
import software.amazon.awssdk.services.codewhispererruntime.model.CreateTaskAssistConversationResponse
import software.amazon.awssdk.services.codewhispererruntime.model.CreateUploadUrlResponse
import software.amazon.awssdk.services.codewhispererruntime.model.GetTaskAssistCodeGenerationResponse
import software.amazon.awssdk.services.codewhispererruntime.model.StartTaskAssistCodeGenerationResponse
import software.amazon.awssdk.services.codewhispererruntime.model.TaskAssistPlanningUploadContext
import software.amazon.awssdk.services.codewhispererruntime.model.UploadContext
import software.amazon.awssdk.services.codewhispererruntime.model.UploadIntent
import software.amazon.awssdk.services.codewhispererstreaming.CodeWhispererStreamingAsyncClient
import software.amazon.awssdk.services.codewhispererstreaming.model.ChatMessage
import software.amazon.awssdk.services.codewhispererstreaming.model.ChatTriggerType
import software.amazon.awssdk.services.codewhispererstreaming.model.ConversationState
import software.amazon.awssdk.services.codewhispererstreaming.model.ExportIntent
import software.amazon.awssdk.services.codewhispererstreaming.model.GenerateTaskAssistPlanRequest
import software.amazon.awssdk.services.codewhispererstreaming.model.GenerateTaskAssistPlanResponseHandler
import software.amazon.awssdk.services.codewhispererstreaming.model.ProgrammingLanguage
import software.amazon.awssdk.services.codewhispererstreaming.model.UserInputMessage
import software.amazon.awssdk.services.codewhispererstreaming.model.WorkspaceState
import software.aws.toolkits.core.utils.error
import software.aws.toolkits.core.utils.getLogger
import software.aws.toolkits.core.utils.info
import software.aws.toolkits.jetbrains.core.awsClient
import software.aws.toolkits.jetbrains.core.credentials.ToolkitConnectionManager
import software.aws.toolkits.jetbrains.core.credentials.pinning.QConnection
import software.aws.toolkits.jetbrains.services.amazonq.clients.AmazonQStreamingClient
import software.aws.toolkits.jetbrains.services.codemodernizer.utils.calculateTotalLatency
import java.time.Instant
import software.amazon.awssdk.services.codewhispererruntime.model.ChatTriggerType as SyncChatTriggerType

@Service(Service.Level.PROJECT)
class FeatureDevClient(private val project: Project) {
    private fun connection() = ToolkitConnectionManager.getInstance(project).activeConnectionForFeature(QConnection.getInstance())
        ?: error("Attempted to use connection while one does not exist")

    private fun bearerClient() = connection().getConnectionSettings().awsClient<CodeWhispererRuntimeClient>()

    private fun streamingBearerClient() = connection().getConnectionSettings().awsClient<CodeWhispererStreamingAsyncClient>()

    private val amazonQStreamingClient
        get() = AmazonQStreamingClient.getInstance(project)

    fun createTaskAssistConversation(): CreateTaskAssistConversationResponse = bearerClient().createTaskAssistConversation(
        CreateTaskAssistConversationRequest.builder().build()
    )

    fun createTaskAssistUploadUrl(conversationId: String, contentChecksumSha256: String, contentLength: Long): CreateUploadUrlResponse =
        bearerClient().createUploadUrl {
            it.contentChecksumType(ContentChecksumType.SHA_256)
                .contentChecksum(contentChecksumSha256)
                .contentLength(contentLength)
                .artifactType(ArtifactType.SOURCE_CODE)
                .uploadIntent(UploadIntent.TASK_ASSIST_PLANNING)
                .uploadContext(
                    UploadContext.builder()
                        .taskAssistPlanningUploadContext(
                            TaskAssistPlanningUploadContext.builder()
                                .conversationId(conversationId)
                                .build()
                        )
                        .build()
                )
        }

    suspend fun generateTaskAssistPlan(conversationId: String, uploadId: String, userMessage: String): GenerateTaskAssistPlanResult {
        val assistantResponse = mutableListOf<String>()
        var succeededPlanning = true

        val result = streamingBearerClient().generateTaskAssistPlan(
            GenerateTaskAssistPlanRequest.builder()
                .conversationState(
                    ConversationState.builder()
                        .currentMessage(
                            ChatMessage.fromUserInputMessage(
                                UserInputMessage.builder()
                                    .content(userMessage)
                                    .build()
                            )
                        )
                        .chatTriggerType(ChatTriggerType.MANUAL)
                        .conversationId(conversationId)
                        .build()
                )
                .workspaceState(
                    WorkspaceState.builder()
                        .programmingLanguage(
                            ProgrammingLanguage.builder()
                                .languageName("javascript")
                                .build()
                        )
                        .uploadId(uploadId)
                        .build()
                )
                .build(),
            GenerateTaskAssistPlanResponseHandler.builder().subscriber(
                GenerateTaskAssistPlanResponseHandler.Visitor.builder()
                    .onAssistantResponseEvent {
                        assistantResponse.add(it.content())
                    }.onInvalidStateEvent {
                        assistantResponse.clear()
                        assistantResponse.add(it.message())
                        succeededPlanning = false
                    }
                    .build()
            )
                .build()
        )

        result.await()
        return GenerateTaskAssistPlanResult(approach = assistantResponse.joinToString(" "), succeededPlanning = succeededPlanning)
    }

    fun startTaskAssistCodeGeneration(conversationId: String, uploadId: String, userMessage: String): StartTaskAssistCodeGenerationResponse = bearerClient()
        .startTaskAssistCodeGeneration {
                request ->
            request
                .conversationState {
                    it
                        .conversationId(conversationId)
                        .chatTriggerType(SyncChatTriggerType.MANUAL)
                        .currentMessage { cm -> cm.userInputMessage { um -> um.content(userMessage) } }
                }
                .workspaceState {
                    it
                        .programmingLanguage { pl -> pl.languageName("javascript") } // This parameter is omitted by featureDev but required in the request
                        .uploadId(uploadId)
                }
        }

    fun getTaskAssistCodeGeneration(conversationId: String, codeGenerationId: String): GetTaskAssistCodeGenerationResponse = bearerClient()
        .getTaskAssistCodeGeneration {
            it
                .conversationId(conversationId)
                .codeGenerationId(codeGenerationId)
        }

    suspend fun exportTaskAssistResultArchive(conversationId: String): MutableList<ByteArray> = amazonQStreamingClient.exportResultArchive(
        conversationId,
        ExportIntent.TASK_ASSIST,
        { e ->
            LOG.error(e) { "TaskAssist - ExportResultArchive stream exportId=$conversationId exportIntent=${ExportIntent.TASK_ASSIST} Failed: ${e.message} " }
        },
        { startTime ->
            LOG.info { "TaskAssist - ExportResultArchive latency: ${calculateTotalLatency(startTime, Instant.now())}" }
        }
    )

    companion object {
        private val LOG = getLogger<FeatureDevClient>()

        fun getInstance(project: Project) = project.service<FeatureDevClient>()
    }
}
