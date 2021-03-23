// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.utils.execution.steps

import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.Project
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import software.aws.toolkits.core.utils.AttributeBag
import software.aws.toolkits.core.utils.AttributeBagKey
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Cross step context that exists for the life of a step workflow execution. Keeps track of the global execution state and allows passing data between steps.
 */
class Context(val project: Project) {
    val workflowToken = UUID.randomUUID().toString()
    private val attributeMap = AttributeBag()
    private val isCancelled = AtomicBoolean(false)
    private val isCompleted = AtomicBoolean(false)

    fun cancel() {
        isCancelled.set(true)
        isCompleted.set(true)
    }

    fun complete() {
        isCompleted.set(true)
    }

    fun isCancelled() = isCancelled.get()
    fun isCompleted() = isCompleted.get()

    fun throwIfCancelled() {
        if (isCancelled()) {
            throw ProcessCanceledException()
        }
    }

    fun <T : Any> getAttribute(key: AttributeBagKey<T>): T? = attributeMap.get(key)

    /**
     * Try to get attribute for timeout milliseconds, throws a kotlinx.coroutines.TimeoutCancellationException on failure
     * @param key The key to try to get
     * @param timeout The timeout in milliseconds
     */
    suspend fun <T : Any> pollingGet(key: AttributeBagKey<T>, timeout: Long = 10000): T = withTimeout(timeout) {
        while (!isCancelled()) {
            val item = attributeMap.get(key)
            if (item != null) {
                return@withTimeout item
            }
            delay(100)
        }
        throw CancellationException("getAttributeOrWait cancelled")
    }

    fun <T : Any> getRequiredAttribute(key: AttributeBagKey<T>): T = attributeMap.getOrThrow(key)

    fun <T : Any> putAttribute(key: AttributeBagKey<T>, data: T) {
        attributeMap.putData(key, data)
    }
}
