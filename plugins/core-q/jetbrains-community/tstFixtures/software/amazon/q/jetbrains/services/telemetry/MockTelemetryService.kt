// Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.amazon.q.jetbrains.services.telemetry

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.replaceService
import org.junit.jupiter.api.extension.AfterEachCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.rules.ExternalResource
import org.mockito.kotlin.reset
import org.mockito.kotlin.spy
import software.amazon.awssdk.services.toolkittelemetry.model.Sentiment
import software.amazon.q.core.telemetry.DefaultTelemetryBatcher
import software.amazon.q.core.telemetry.MetricEvent
import software.amazon.q.core.telemetry.TelemetryBatcher
import software.amazon.q.core.telemetry.TelemetryPublisher

class NoOpTelemetryService : TelemetryService {
    constructor(noOpPublisher: NoOpPublisher, batcher: TelemetryBatcher) : super(noOpPublisher, batcher)
    constructor() : this(NO_OP_PUBLISHER, DefaultTelemetryBatcher(NO_OP_PUBLISHER))

    fun batcher() = super.batcher

    companion object {
        val NO_OP_PUBLISHER = NoOpPublisher()
    }
}

class NoOpPublisher : TelemetryPublisher {
    override suspend fun publish(metricEvents: Collection<MetricEvent>) {}

    override suspend fun sendFeedback(sentiment: Sentiment, comment: String, metadata: Map<String, String>) {}

    override fun close() {}
}

sealed class MockTelemetryServiceBase : ExternalResource() {
    protected val publisher: NoOpPublisher by lazy { NoOpTelemetryService.NO_OP_PUBLISHER }
    protected val batcher: TelemetryBatcher by lazy { spy(DefaultTelemetryBatcher(publisher)) }
    private lateinit var disposableParent: Disposable

    private val mockTelemetryService: NoOpTelemetryService by lazy { NoOpTelemetryService(publisher, batcher) }

    override fun before() {
        // hack because @TestDisposable doesn't work here as it's not a test
        disposableParent = Disposer.newDisposable()
        ApplicationManager.getApplication().replaceService(TelemetryService::class.java, mockTelemetryService, disposableParent)
    }

    override fun after() {
        reset(batcher())
        Disposer.dispose(disposableParent)
    }

    fun telemetryService() = mockTelemetryService
    fun batcher() = mockTelemetryService.batcher()
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
