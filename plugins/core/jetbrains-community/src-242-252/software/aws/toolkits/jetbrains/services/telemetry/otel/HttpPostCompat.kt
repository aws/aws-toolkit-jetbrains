// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.telemetry.otel

import com.intellij.platform.util.http.ContentType
import com.intellij.platform.util.http.httpPost
import io.opentelemetry.exporter.internal.otlp.traces.TraceRequestMarshaler

/**
 * Version-specific compatibility wrapper for httpPost (2024.2-2025.2)
 * In these versions, httpPost uses contentLength parameter with streaming body
 */
internal suspend fun sendOtelTrace(url: String, marshaler: TraceRequestMarshaler) {
    httpPost(url, contentLength = marshaler.binarySerializedSize.toLong(), contentType = ContentType.XProtobuf) {
        marshaler.writeBinaryTo(this)
    }
}
