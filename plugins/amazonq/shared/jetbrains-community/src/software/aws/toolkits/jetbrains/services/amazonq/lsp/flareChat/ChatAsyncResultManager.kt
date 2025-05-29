// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonq.lsp.flareChat

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * Manages asynchronous results for chat operations, particularly handling the coordination
 * between partial results and final results during cancellation.
 */
@Service(Service.Level.PROJECT)
class ChatAsyncResultManager() {
    private val results = ConcurrentHashMap<String, CompletableFuture<Any>>()
    private val completedResults = ConcurrentHashMap<String, Any>()
    private val timeout = 30L
    private val timeUnit = TimeUnit.SECONDS

    fun createRequestId(requestId: String) {
        if (!completedResults.containsKey(requestId)) {
            results[requestId] = CompletableFuture()
        }
    }

    fun removeRequestId(requestId: String) {
        val future = results.remove(requestId)
        if (future != null && !future.isDone) {
            future.cancel(true)
        }
        completedResults.remove(requestId)
    }

    fun setResult(requestId: String, result: Any) {
        val future = results[requestId]
        if (future != null) {
            future.complete(result)
            results.remove(requestId)
        }
        completedResults[requestId] = result
    }

    fun getResult(requestId: String): Any? {
        return getResult(requestId, timeout, timeUnit)
    }

    private fun getResult(requestId: String, timeout: Long, unit: TimeUnit): Any? {
        val completedResult = completedResults[requestId]
        if (completedResult != null) {
            return completedResult
        }

        val future = results[requestId] ?: throw IllegalArgumentException("Request ID not found: $requestId")

        try {
            val result = future.get(timeout, unit)
            completedResults[requestId] = result
            results.remove(requestId)
            return result
        } catch (e: TimeoutException) {
            future.cancel(true)
            results.remove(requestId)
            throw TimeoutException("Operation timed out for requestId: $requestId")
        }
    }

    companion object {
        fun getInstance(project: Project) = project.service<ChatAsyncResultManager>()
    }
}
