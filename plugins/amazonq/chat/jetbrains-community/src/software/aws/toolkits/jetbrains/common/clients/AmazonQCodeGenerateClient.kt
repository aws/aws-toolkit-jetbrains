// Copyright 2023 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.common.clients

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.SystemInfo
import software.amazon.awssdk.services.codewhispererruntime.CodeWhispererRuntimeClient
import software.amazon.awssdk.services.codewhispererruntime.model.ArtifactType
import software.amazon.awssdk.services.codewhispererruntime.model.ContentChecksumType
import software.amazon.awssdk.services.codewhispererruntime.model.CreateTaskAssistConversationRequest
import software.amazon.awssdk.services.codewhispererruntime.model.CreateTaskAssistConversationResponse
import software.amazon.awssdk.services.codewhispererruntime.model.CreateUploadUrlResponse
import software.amazon.awssdk.services.codewhispererruntime.model.Dimension
import software.amazon.awssdk.services.codewhispererruntime.model.DocV2AcceptanceEvent
import software.amazon.awssdk.services.codewhispererruntime.model.DocV2GenerationEvent
import software.amazon.awssdk.services.codewhispererruntime.model.GetTaskAssistCodeGenerationResponse
import software.amazon.awssdk.services.codewhispererruntime.model.IdeCategory
import software.amazon.awssdk.services.codewhispererruntime.model.OperatingSystem
import software.amazon.awssdk.services.codewhispererruntime.model.OptOutPreference
import software.amazon.awssdk.services.codewhispererruntime.model.SendTelemetryEventResponse
import software.amazon.awssdk.services.codewhispererruntime.model.StartTaskAssistCodeGenerationResponse
import software.amazon.awssdk.services.codewhispererruntime.model.TaskAssistPlanningUploadContext
import software.amazon.awssdk.services.codewhispererruntime.model.UploadContext
import software.amazon.awssdk.services.codewhispererruntime.model.UploadIntent
import software.amazon.awssdk.services.codewhispererruntime.model.UserContext
import software.amazon.awssdk.services.codewhispererstreaming.model.ExportIntent
import software.aws.toolkits.core.utils.error
import software.aws.toolkits.core.utils.getLogger
import software.aws.toolkits.core.utils.info
import software.aws.toolkits.jetbrains.common.session.Intent
import software.aws.toolkits.jetbrains.core.awsClient
import software.aws.toolkits.jetbrains.core.credentials.ToolkitConnectionManager
import software.aws.toolkits.jetbrains.core.credentials.pinning.QConnection
import software.aws.toolkits.jetbrains.services.amazonq.clients.AmazonQStreamingClient
import software.aws.toolkits.jetbrains.services.amazonqDoc.FEATURE_EVALUATION_PRODUCT_NAME
import software.aws.toolkits.jetbrains.services.codemodernizer.utils.calculateTotalLatency
import software.aws.toolkits.jetbrains.services.telemetry.ClientMetadata
import software.aws.toolkits.jetbrains.settings.AwsSettings
import java.time.Instant
import software.amazon.awssdk.services.codewhispererruntime.model.ChatTriggerType as SyncChatTriggerType

@Service(Service.Level.PROJECT)
class AmazonQCodeGenerateClient(private val project: Project) {
    private fun getTelemetryOptOutPreference() =
        if (AwsSettings.getInstance().isTelemetryEnabled) {
            OptOutPreference.OPTIN
        } else {
            OptOutPreference.OPTOUT
        }

    private val docUserContext = ClientMetadata.getDefault().let {
        val osForFeatureDev: OperatingSystem =
            when {
                SystemInfo.isWindows -> OperatingSystem.WINDOWS
                SystemInfo.isMac -> OperatingSystem.MAC
                // For now, categorize everything else as "Linux" (Linux/FreeBSD/Solaris/etc.)
                else -> OperatingSystem.LINUX
            }

        UserContext.builder()
            .ideCategory(IdeCategory.JETBRAINS)
            .operatingSystem(osForFeatureDev)
            .product(FEATURE_EVALUATION_PRODUCT_NAME)
            .clientId(it.clientId)
            .ideVersion(it.awsVersion)
            .build()
    }

    fun connection() = ToolkitConnectionManager.getInstance(project).activeConnectionForFeature(QConnection.getInstance())
        ?: error("Attempted to use connection while one does not exist")

    fun bearerClient() = connection().getConnectionSettings().awsClient<CodeWhispererRuntimeClient>()

    private val amazonQStreamingClient
        get() = AmazonQStreamingClient.getInstance(project)

    fun sendDocTelemetryEvent(
        generationEvent: DocV2GenerationEvent? = null,
        acceptanceEvent: DocV2AcceptanceEvent? = null,
    ): SendTelemetryEventResponse =
        bearerClient().sendTelemetryEvent { requestBuilder ->
            requestBuilder.telemetryEvent { telemetryEventBuilder ->
                generationEvent?.let { telemetryEventBuilder.docV2GenerationEvent(it) }
                acceptanceEvent?.let { telemetryEventBuilder.docV2AcceptanceEvent(it) }
            }
            requestBuilder.optOutPreference(getTelemetryOptOutPreference())
            requestBuilder.userContext(docUserContext)
        }

    fun sendDocMetricData(operationName: String, result: String): SendTelemetryEventResponse =
        bearerClient().sendTelemetryEvent { requestBuilder ->
            requestBuilder.telemetryEvent { telemetryEventBuilder ->
                telemetryEventBuilder.metricData {
                    it
                        .metricName("Operation")
                        .metricValue(1.0)
                        .timestamp(Instant.now())
                        .product("DocGeneration")
                        .dimensions(
                            listOf(
                                Dimension.builder()
                                    .name("operationName")
                                    .value(operationName)
                                    .build(),
                                Dimension.builder()
                                    .name("result")
                                    .value(result)
                                    .build()
                            )
                        )
                }
            }
            requestBuilder.optOutPreference(getTelemetryOptOutPreference())
            requestBuilder.userContext(docUserContext)
        }

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

    fun startTaskAssistCodeGeneration(conversationId: String, uploadId: String, userMessage: String, intent: Intent): StartTaskAssistCodeGenerationResponse =
        bearerClient()
            .startTaskAssistCodeGeneration { request ->
                request
                    .conversationState {
                        it
                            .conversationId(conversationId)
                            .chatTriggerType(SyncChatTriggerType.MANUAL)
                            .currentMessage { cm -> cm.userInputMessage { um -> um.content(userMessage) } }
                    }
                    .workspaceState {
                        it
                            .programmingLanguage { pl -> pl.languageName("javascript") }
                            .uploadId(uploadId)
                    }
                    .intent(intent.name)
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
        null,
        { e ->
            LOG.error(e) { "TaskAssist - ExportResultArchive stream exportId=$conversationId exportIntent=${ExportIntent.TASK_ASSIST} Failed: ${e.message} " }
        },
        { startTime ->
            LOG.info { "TaskAssist - ExportResultArchive latency: ${calculateTotalLatency(startTime, Instant.now())}" }
        }
    )

    companion object {
        private val LOG = getLogger<AmazonQCodeGenerateClient>()

        fun getInstance(project: Project) = project.service<AmazonQCodeGenerateClient>()
    }
}
