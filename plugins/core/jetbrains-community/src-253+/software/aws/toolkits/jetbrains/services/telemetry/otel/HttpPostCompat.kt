// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.telemetry.otel

import com.intellij.platform.util.http.ContentType
import com.intellij.platform.util.http.httpPost
import io.opentelemetry.exporter.internal.otlp.traces.TraceRequestMarshaler
import java.io.ByteArrayOutputStream

/**
 * Version-specific compatibility wrapper for httpPost (2025.3+)
 * In this version, httpPost uses body parameter with byte array
 */
internal suspend fun sendOtelTrace(url: String, marshaler: TraceRequestMarshaler) {
    val output = ByteArrayOutputStream()
    marshaler.writeBinaryTo(output)
    httpPost(url, contentType = ContentType.XProtobuf, body = output.toByteArray())
}
