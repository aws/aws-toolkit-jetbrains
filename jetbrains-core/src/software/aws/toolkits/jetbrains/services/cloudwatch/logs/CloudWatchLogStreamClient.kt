// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cloudwatch.logs

import com.intellij.openapi.project.Project
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.future.asDeferred
import software.amazon.awssdk.services.cloudwatchlogs.CloudWatchLogsAsyncClient
import software.amazon.awssdk.services.cloudwatchlogs.model.GetLogEventsRequest
import software.amazon.awssdk.services.cloudwatchlogs.model.OutputLogEvent
import software.aws.toolkits.jetbrains.core.awsClient
import software.aws.toolkits.jetbrains.utils.ApplicationThreadPoolScope
import java.util.concurrent.atomic.AtomicBoolean

class CloudWatchLogStreamClient(
    project: Project,
    private val logGroup: String,
    private val logStream: String
) : CoroutineScope by ApplicationThreadPoolScope("CloudWatchLogsStream") {
    private val client: CloudWatchLogsAsyncClient = project.awsClient()
    private var nextBackwardToken: String? = null
    private var nextForwardToken: String? = null
    private val loadingForwardEvent: AtomicBoolean = AtomicBoolean(false)
    private val loadingBackwardEvent: AtomicBoolean = AtomicBoolean(false)

    suspend fun loadInitialAround(startTime: Long, timeScale: Long): List<OutputLogEvent> {
        try {
            loadingForwardEvent.set(true)
            loadingBackwardEvent.set(true)
            val request = GetLogEventsRequest
                .builder()
                .logGroupName(logGroup)
                .logStreamName(logStream)
                .startTime(startTime - timeScale)
                .endTime(startTime + timeScale)
                .build()
            return load(request, saveForwardToken = true, saveBackwardToken = true)
        } finally {
            loadingBackwardEvent.set(false)
            loadingForwardEvent.set(false)
        }
    }

    suspend fun loadInitial(fromHead: Boolean): List<OutputLogEvent> {
        try {
            loadingForwardEvent.set(true)
            loadingBackwardEvent.set(true)
            val request = GetLogEventsRequest.builder().logGroupName(logGroup).logStreamName(logStream).startFromHead(fromHead).build()
            return load(request, saveForwardToken = true, saveBackwardToken = true)
        } finally {
            loadingBackwardEvent.set(false)
            loadingForwardEvent.set(false)
        }
    }

    suspend fun loadMoreForward(): List<OutputLogEvent> = loadMore(loadingForwardEvent, nextForwardToken, saveForwardToken = true)

    suspend fun loadMoreBackward(): List<OutputLogEvent> = loadMore(loadingBackwardEvent, nextBackwardToken, saveBackwardToken = true)

    private suspend fun loadMore(
        loadingGuard: AtomicBoolean,
        nextToken: String?,
        saveForwardToken: Boolean = false,
        saveBackwardToken: Boolean = false
    ): List<OutputLogEvent> {
        val alreadyLoading = loadingGuard.getAndSet(true)
        if (alreadyLoading) {
            return listOf()
        }

        try {
            val request = GetLogEventsRequest
                .builder()
                .logGroupName(logGroup)
                .logStreamName(logStream)
                .startFromHead(true)
                .nextToken(nextToken)
                .build()
            return load(request, saveForwardToken = saveForwardToken, saveBackwardToken = saveBackwardToken)
        } finally {
            loadingGuard.set(false)
        }
    }

    private suspend fun load(
        request: GetLogEventsRequest,
        saveForwardToken: Boolean = false,
        saveBackwardToken: Boolean = false
    ): List<OutputLogEvent> {
        val response = client.getLogEvents(request).asDeferred().await()
        val events = response.events().filterNotNull()
        if (saveForwardToken) {
            nextForwardToken = response.nextForwardToken()
        }
        if (saveBackwardToken) {
            nextBackwardToken = response.nextBackwardToken()
        }

        return events
    }
}
