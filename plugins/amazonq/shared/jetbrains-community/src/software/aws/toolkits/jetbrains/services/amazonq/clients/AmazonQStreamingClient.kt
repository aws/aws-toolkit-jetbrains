// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonq.clients

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import kotlinx.coroutines.future.await
import software.amazon.awssdk.core.exception.SdkException
import software.amazon.awssdk.services.codewhispererstreaming.CodeWhispererStreamingAsyncClient
import software.amazon.awssdk.services.codewhispererstreaming.model.ExportContext
import software.amazon.awssdk.services.codewhispererstreaming.model.ExportIntent
import software.amazon.awssdk.services.codewhispererstreaming.model.ExportResultArchiveResponseHandler
import software.amazon.awssdk.services.codewhispererstreaming.model.ThrottlingException
import software.amazon.awssdk.services.codewhispererstreaming.model.ValidationException
import software.aws.toolkits.core.utils.getLogger
import software.aws.toolkits.core.utils.warn
import software.aws.toolkits.jetbrains.services.amazonq.RetryableOperation
import software.aws.toolkits.jetbrains.services.amazonq.profile.QRegionProfileManager
import java.time.Instant
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicReference

@Service(Service.Level.PROJECT)
class AmazonQStreamingClient(private val project: Project) {
    private fun streamingBearerClient() = QRegionProfileManager.getInstance().getQClient<CodeWhispererStreamingAsyncClient>(project)

    suspend fun exportResultArchive(
        exportId: String,
        exportIntent: ExportIntent,
        exportContext: ExportContext?,
        /**
         * Handler for streaming exceptions.
         *
         * Useful for cases where some exception handling is needed. e.g. log data or send telemetry.
         *
         * The client will then raise the exception to the callee.
         *
         * @param e exception thrown by the streaming client.
         */
        onError: (e: Exception) -> Unit,
        /**
         * Handler for extra logic after streaming ends.
         *
         * Useful for cases where some exception handling is needed. e.g. log data or send telemetry.
         *
         * @param startTime exception thrown by the streaming client.
         */
        onStreamingFinished: (startTime: Instant) -> Unit,
    ): MutableList<ByteArray> {
        val startTime = Instant.now()
        val byteBufferList = mutableListOf<ByteArray>()
        val checksum = AtomicReference("")

        try {
            RetryableOperation<Unit>().executeSuspend(
                operation = {
                    val result = streamingBearerClient().exportResultArchive(
                        {
                            it.exportId(exportId)
                            it.exportIntent(exportIntent)
                            it.exportContext(exportContext)
                        },
                        ExportResultArchiveResponseHandler.builder().subscriber(
                            ExportResultArchiveResponseHandler.Visitor.builder()
                                .onBinaryMetadataEvent {
                                    checksum.set(it.contentChecksum())
                                }.onBinaryPayloadEvent {
                                    val payloadBytes = it.bytes().asByteArray()
                                    byteBufferList.add(payloadBytes)
                                }.onDefault {
                                    LOG.warn { "Received unknown payload stream: $it" }
                                }
                                .build()
                        )
                            .build()
                    )
                    result.await()
                },
                isRetryable = { e ->
                    when (e) {
                        is ValidationException,
                        is ThrottlingException,
                        is SdkException,
                        is TimeoutException,
                        -> true
                        else -> false
                    }
                },
                errorHandler = { e, attempts ->
                    onError(e)
                    throw e
                }
            )
        } finally {
            onStreamingFinished(startTime)
        }

        return byteBufferList
    }

    companion object {
        private val LOG = getLogger<AmazonQStreamingClient>()

        fun getInstance(project: Project) = project.service<AmazonQStreamingClient>()
    }
}
