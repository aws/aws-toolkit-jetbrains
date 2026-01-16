// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkit.jetbrains.core

import com.intellij.openapi.project.Project
import com.intellij.testFramework.HeavyPlatformTestCase
import com.intellij.ui.jcef.JBCefBrowserBase
import com.intellij.ui.jcef.JBCefJSQuery
import org.assertj.core.api.Assertions.assertThat
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import software.aws.toolkit.core.telemetry.MetricEvent
import software.aws.toolkit.jetbrains.core.webview.BrowserMessage
import software.aws.toolkit.jetbrains.core.webview.BrowserState
import software.aws.toolkit.jetbrains.core.webview.LoginBrowser
import software.aws.toolkit.jetbrains.services.telemetry.MockTelemetryServiceExtension

class TestLoginBrowser(project: Project) : LoginBrowser(project) {
    // test env can't initiate a real jcef and will throw error
    override val jcefBrowser: JBCefBrowserBase
        get() = mock()

    override fun handleBrowserMessage(message: BrowserMessage?) {}

    override fun prepareBrowser(state: BrowserState) {}

    override fun loadWebView(query: JBCefJSQuery) {}
}

class LoginBrowserTest : HeavyPlatformTestCase() {
    private lateinit var sut: TestLoginBrowser
    private val mockTelemetryService = MockTelemetryServiceExtension()

    override fun setUp() {
        super.setUp()
        mockTelemetryService.beforeEach(null)
        sut = TestLoginBrowser(project)
    }

    override fun tearDown() {
        try {
            mockTelemetryService.afterEach(null)
        } finally {
            super.tearDown()
        }
    }
    fun `test publish telemetry happy path`() {
        val load = """
            {
                "metricName": "toolkit_didLoadModule",
                "module": "login",
                "result": "Succeeded",
                "duration": "0"
            }
        """.trimIndent()
        val message = BrowserMessage.PublishWebviewTelemetry(load)
        sut.publishTelemetry(message)

        mockTelemetryService.batcher()
        argumentCaptor<MetricEvent> {
            verify(mockTelemetryService.batcher()).enqueue(capture())
            val event = requireNotNull(firstValue.data.find { it.name == "toolkit_didLoadModule" })
            assertThat(event)
                .matches { it.metadata["module"] == "login" }
                .matches { it.metadata["result"] == "Succeeded" }
                .matches { it.metadata["duration"] == "0.0" }
        }
    }
    fun `test publish telemetry error path`() {
        val load = """
            {
                "metricName": "toolkit_didLoadModule",
                "module": "login",
                "result": "Failed",
                "reason": "unexpected error"
            }
        """.trimIndent()
        val message = BrowserMessage.PublishWebviewTelemetry(load)
        sut.publishTelemetry(message)

        mockTelemetryService.batcher()
        argumentCaptor<MetricEvent> {
            verify(mockTelemetryService.batcher()).enqueue(capture())
            val event = requireNotNull(firstValue.data.find { it.name == "toolkit_didLoadModule" })
            assertThat(event)
                .matches { it.metadata["module"] == "login" }
                .matches { it.metadata["result"] == "Failed" }
                .matches { it.metadata["reason"] == "unexpected error" }
        }
    }
    fun `test missing required field will do nothing`() {
        val load = """
            {
                "metricName": "toolkit_didLoadModule"
            }
        """.trimIndent()
        val message = BrowserMessage.PublishWebviewTelemetry(load)
        sut.publishTelemetry(message)

        val load1 = """
            {
                "metricName": "toolkit_didLoadModule",
                "module": "login"
            }
        """.trimIndent()
        val message1 = BrowserMessage.PublishWebviewTelemetry(load1)
        sut.publishTelemetry(message1)

        val load2 = """
            {
                "metricName": "toolkit_didLoadModule",
                "result": "Failed"
            }
        """.trimIndent()
        val message2 = BrowserMessage.PublishWebviewTelemetry(load2)
        sut.publishTelemetry(message2)

        mockTelemetryService.batcher()
        argumentCaptor<MetricEvent> {
            verify(mockTelemetryService.batcher(), times(0)).enqueue(capture())
        }
    }
    fun `test metricName doesn't match will do nothing`() {
        val load = """
            {
                "metricName": "foo",
                "module": "login",
                "result": "Failed",
                "reason": "unexpected error"
            }
        """.trimIndent()
        val message = BrowserMessage.PublishWebviewTelemetry(load)
        sut.publishTelemetry(message)

        mockTelemetryService.batcher()
        argumentCaptor<MetricEvent> {
            verify(mockTelemetryService.batcher(), times(0)).enqueue(capture())
        }
    }
}
