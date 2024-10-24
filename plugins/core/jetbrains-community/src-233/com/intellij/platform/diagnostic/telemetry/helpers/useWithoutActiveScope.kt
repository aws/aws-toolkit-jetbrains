// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.intellij.platform.diagnostic.telemetry.helpers

import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.StatusCode
import kotlin.coroutines.cancellation.CancellationException

val EXCEPTION_ESCAPED = AttributeKey.booleanKey("exception.escaped")

inline fun <T> Span.useWithoutActiveScope(operation: (Span) -> T): T {
    try {
        return operation(this)
    }
    catch (e: CancellationException) {
        recordException(e, Attributes.of(EXCEPTION_ESCAPED, true))
        throw e
    }
    catch (e: Throwable) {
        recordException(e, Attributes.of(EXCEPTION_ESCAPED, true))
        setStatus(StatusCode.ERROR)
        throw e
    }
    finally {
        end()
    }
}
