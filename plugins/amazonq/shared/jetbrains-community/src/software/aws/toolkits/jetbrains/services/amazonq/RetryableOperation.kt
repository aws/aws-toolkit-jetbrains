// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonq

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import software.amazon.awssdk.core.exception.RetryableException
import kotlin.random.Random

class RetryableOperation<T> {
    private var attempts = 0
    private var currentDelay = INITIAL_DELAY

    private fun getJitteredDelay(): Long {
        currentDelay = (currentDelay * 2).coerceAtMost(MAX_BACKOFF)
        return (currentDelay * (0.5 + Random.nextDouble(0.5))).toLong()
    }

    fun execute(
        operation: () -> T,
        isRetryable: (Exception) -> Boolean = { it is RetryableException },
        errorHandler: ((Exception, Int) -> Nothing),
    ): T = runBlocking {
        executeSuspend(operation, isRetryable, errorHandler)
    }

    suspend fun executeSuspend(
        operation: suspend () -> T,
        isRetryable: (Exception) -> Boolean = { it is RetryableException },
        errorHandler: (suspend (Exception, Int) -> Nothing),
    ): T {
        while (attempts < MAX_ATTEMPTS) {
            try {
                return operation()
            } catch (e: Exception) {
                attempts++
                if (attempts >= MAX_ATTEMPTS || !isRetryable(e)) {
                    errorHandler.invoke(e, attempts)
                }
                delay(getJitteredDelay())
            }
        }

        throw RuntimeException("Unexpected state after $attempts attempts")
    }

    companion object {
        private const val INITIAL_DELAY = 100L
        private const val MAX_BACKOFF = 10000L
        private const val MAX_ATTEMPTS = 4
    }
}
