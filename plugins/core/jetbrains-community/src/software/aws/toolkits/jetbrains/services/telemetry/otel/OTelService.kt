// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0
@file:Suppress("UnusedPrivateClass")

package software.aws.toolkits.jetbrains.services.telemetry.otel

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.serviceContainer.NonInjectable
import com.intellij.util.io.HttpRequests
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator
import io.opentelemetry.context.Context
import io.opentelemetry.context.propagation.ContextPropagators
import io.opentelemetry.exporter.internal.otlp.traces.TraceRequestMarshaler
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.resources.Resource
import io.opentelemetry.sdk.trace.ReadWriteSpan
import io.opentelemetry.sdk.trace.ReadableSpan
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.trace.SpanProcessor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider
import software.amazon.awssdk.http.ContentStreamProvider
import software.amazon.awssdk.http.HttpExecuteRequest
import software.amazon.awssdk.http.SdkHttpMethod
import software.amazon.awssdk.http.SdkHttpRequest
import software.amazon.awssdk.http.apache.ApacheHttpClient
import software.amazon.awssdk.http.auth.aws.signer.AwsV4HttpSigner
import software.aws.toolkits.jetbrains.core.AwsClientManager
import java.io.ByteArrayOutputStream
import java.net.ConnectException

private class BasicOtlpSpanProcessor(
    private val coroutineScope: CoroutineScope,
    private val traceUrl: String = "http://127.0.0.1:4318/v1/traces",
) : SpanProcessor {
    override fun onStart(parentContext: Context, span: ReadWriteSpan) {}
    override fun isStartRequired() = false
    override fun isEndRequired() = true

    override fun onEnd(span: ReadableSpan) {
        val data = span.toSpanData()
        coroutineScope.launch {
            try {
                val item = TraceRequestMarshaler.create(listOf(data))

                HttpRequests.post(traceUrl, "application/x-protobuf")
                    .userAgent(AwsClientManager.getUserAgent())
                    .connect { request ->
                        item.writeBinaryTo(request.connection.outputStream)
                    }
            } catch (e: CancellationException) {
                throw e
            } catch (e: ConnectException) {
                thisLogger().warn("Cannot export (url=$traceUrl): ${e.message}")
            } catch (e: Throwable) {
                thisLogger().error("Cannot export (url=$traceUrl)", e)
            }
        }
    }
}

private class SigV4OtlpSpanProcessor(
    private val coroutineScope: CoroutineScope,
    private val traceUrl: String,
    private val creds: AwsCredentialsProvider,
) : SpanProcessor {
    override fun onStart(parentContext: Context, span: ReadWriteSpan) {}
    override fun isStartRequired() = false
    override fun isEndRequired() = true

    private val client = ApacheHttpClient.create()

    override fun onEnd(span: ReadableSpan) {
        coroutineScope.launch {
            val data = span.toSpanData()
            try {
                val item = TraceRequestMarshaler.create(listOf(data))
                // calculate the sigv4 header
                val signer = AwsV4HttpSigner.create()
                val httpRequest =
                    SdkHttpRequest.builder()
                        .uri(traceUrl)
                        .method(SdkHttpMethod.POST)
                        .putHeader("Content-Type", "application/x-protobuf")
                        .build()

                val baos = ByteArrayOutputStream()
                item.writeBinaryTo(baos)
                val payload = ContentStreamProvider.fromByteArray(baos.toByteArray())
                val signedRequest = signer.sign {
                    it.identity(creds.resolveIdentity().get())
                    it.request(httpRequest)
                    it.payload(payload)
                    it.putProperty(AwsV4HttpSigner.SERVICE_SIGNING_NAME, "osis")
                    it.putProperty(AwsV4HttpSigner.REGION_NAME, "us-west-2")
                }

                // Create and HTTP client and send the request. ApacheHttpClient requires the 'apache-client' module.
                client.prepareRequest(
                    HttpExecuteRequest.builder()
                        .request(signedRequest.request())
                        .contentStreamProvider(signedRequest.payload().orElse(null))
                        .build()
                ).call()
            } catch (e: CancellationException) {
                throw e
            } catch (e: ConnectException) {
                thisLogger().warn("Cannot export (url=$traceUrl): ${e.message}")
            } catch (e: Throwable) {
                thisLogger().error("Cannot export (url=$traceUrl)", e)
            }
        }
    }
}

private object StdoutSpanProcessor : SpanProcessor {
    override fun onStart(parentContext: Context, span: ReadWriteSpan) {}
    override fun isStartRequired() = false
    override fun isEndRequired() = true

    override fun onEnd(span: ReadableSpan) {
        println(span.toSpanData())
    }
}

@Service
class OTelService @NonInjectable internal constructor(spanProcessors: List<SpanProcessor>) : Disposable {
    @Suppress("unused")
    constructor() : this(listOf(ToolkitTelemetryOTelSpanProcessor()))

    private val sdkDelegate = lazy {
        OpenTelemetrySdk.builder()
            .setTracerProvider(
                SdkTracerProvider.builder()
                    .apply {
                        spanProcessors.forEach {
                            addSpanProcessor(it)
                        }
                    }
                    .setResource(
                        Resource.create(
                            Attributes.builder()
                                .put(AttributeKey.stringKey("os.type"), SystemInfoRt.OS_NAME)
                                .put(AttributeKey.stringKey("os.version"), SystemInfoRt.OS_VERSION)
                                .put(AttributeKey.stringKey("host.arch"), System.getProperty("os.arch"))
                                .build()
                        )
                    )
                    .build()
            )
            .setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance()))
            .build()
    }
    internal val sdk: OpenTelemetrySdk by sdkDelegate

    override fun dispose() {
        if (sdkDelegate.isInitialized()) {
            sdk.close()
        }
    }

    companion object {
        fun getSdk() = service<OTelService>().sdk
    }
}
