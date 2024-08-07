// Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.telemetry

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.replaceService
import org.junit.jupiter.api.extension.AfterEachCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.rules.ExternalResource
import org.mockito.Mockito.mock
import org.mockito.Mockito.spy
import software.amazon.awssdk.services.toolkittelemetry.model.Sentiment
import software.aws.toolkits.core.telemetry.MetricEvent
import software.aws.toolkits.core.telemetry.TelemetryPublisher

class NoOpTelemetryService : TelemetryService(mock(), mock()) {
    fun batcher() = super.batcher
    fun publisher() = super.publisher
}

class NoOpPublisher : TelemetryPublisher {
    override suspend fun publish(metricEvents: Collection<MetricEvent>) {}

    override suspend fun sendFeedback(sentiment: Sentiment, comment: String, metadata: Map<String, String>) {}

    override fun close() {}
}

sealed class MockTelemetryServiceBase : ExternalResource() {
    private lateinit var mockTelemetryService: NoOpTelemetryService
    private lateinit var parentDisposable: Disposable

    override fun before() {
        parentDisposable = Disposer.newDisposable()
        mockTelemetryService = spy(NoOpTelemetryService())
        ApplicationManager.getApplication().replaceService(TelemetryService::class.java, mockTelemetryService, parentDisposable)
    }

    override fun after() {
        Disposer.dispose(parentDisposable)
        Disposer.dispose(mockTelemetryService)
    }

    fun telemetryService() = mockTelemetryService
    fun batcher() = mockTelemetryService.batcher()
    fun publisher() = mockTelemetryService.publisher()
}

class MockTelemetryServiceRule : MockTelemetryServiceBase()

class MockTelemetryServiceExtension : MockTelemetryServiceBase(), BeforeEachCallback, AfterEachCallback {
    override fun beforeEach(context: ExtensionContext?) {
        before()
    }

    override fun afterEach(context: ExtensionContext?) {
        after()
    }
}
