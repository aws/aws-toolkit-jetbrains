// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0
@file:Suppress("BannedImports")

package software.aws.toolkits.jetbrains.services.amazonq.lsp

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.ToNumberPolicy
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.testFramework.ApplicationExtension
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.entry
import org.eclipse.lsp4j.ConfigurationItem
import org.eclipse.lsp4j.ConfigurationParams
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import software.amazon.awssdk.services.toolkittelemetry.model.MetricUnit
import software.aws.toolkits.core.telemetry.DefaultMetricEvent
import software.aws.toolkits.core.telemetry.MetricEvent
import software.aws.toolkits.jetbrains.core.credentials.AwsBearerTokenConnection
import software.aws.toolkits.jetbrains.core.credentials.ToolkitConnectionManager
import software.aws.toolkits.jetbrains.core.credentials.pinning.QConnection
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.credentials.ConnectionMetadata
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.credentials.SsoProfileData
import software.aws.toolkits.jetbrains.services.telemetry.TelemetryService
import software.aws.toolkits.jetbrains.settings.CodeWhispererSettings

@ExtendWith(ApplicationExtension::class)
class AmazonQLanguageClientImplTest {
    private val project: Project = mockk(relaxed = true)
    private val sut = AmazonQLanguageClientImpl(project)

    @Test
    fun `telemetryEvent handles basic event with name and data`() {
        val telemetryService = mockk<TelemetryService>(relaxed = true)
        mockkObject(TelemetryService)
        every { TelemetryService.getInstance() } returns telemetryService

        val builderCaptor = slot<MetricEvent.Builder.() -> Unit>()
        every { telemetryService.record(project, capture(builderCaptor)) } returns Unit

        val event = mapOf(
            "name" to "test_event",
            "data" to mapOf(
                "key1" to "value1",
                "key2" to 42
            )
        )

        sut.telemetryEvent(event)

        val builder = DefaultMetricEvent.builder()
        builderCaptor.captured.invoke(builder)

        val metricEvent = builder.build()
        val datum = metricEvent.data.first()

        assertThat(datum.name).isEqualTo("test_event")
        assertThat(datum.metadata).contains(
            entry("key1", "value1"),
            entry("key2", "42")
        )
    }

    @Test
    fun `telemetryEvent handles event with result field`() {
        val telemetryService = mockk<TelemetryService>(relaxed = true)
        mockkObject(TelemetryService)
        every { TelemetryService.getInstance() } returns telemetryService

        val builderCaptor = slot<MetricEvent.Builder.() -> Unit>()
        every { telemetryService.record(project, capture(builderCaptor)) } returns Unit

        val event = mapOf(
            "name" to "test_event",
            "result" to "success",
            "data" to mapOf(
                "key1" to "value1"
            )
        )

        sut.telemetryEvent(event)

        val builder = DefaultMetricEvent.builder()
        builderCaptor.captured.invoke(builder)

        val metricEvent = builder.build()
        val datum = metricEvent.data.first()

        assertThat(datum.name).isEqualTo("test_event")
        assertThat(datum.metadata).contains(
            entry("key1", "value1"),
            entry("result", "success")
        )
    }

    @Test
    fun `telemetryEvent uses custom unit value when provided`() {
        val telemetryService = mockk<TelemetryService>(relaxed = true)
        mockkObject(TelemetryService)
        every { TelemetryService.getInstance() } returns telemetryService

        val builderCaptor = slot<MetricEvent.Builder.() -> Unit>()
        every { telemetryService.record(project, capture(builderCaptor)) } returns Unit

        val event = mapOf(
            "name" to "test_event",
            "unit" to "Bytes",
            "data" to mapOf(
                "key1" to "value1"
            )
        )

        sut.telemetryEvent(event)

        val builder = DefaultMetricEvent.builder()
        builderCaptor.captured.invoke(builder)

        val metricEvent = builder.build()
        val datum = metricEvent.data.first()

        assertThat(datum.unit).isEqualTo(MetricUnit.BYTES)
        assertThat(datum.metadata).contains(entry("key1", "value1"))
    }

    @Test
    fun `telemetryEvent uses custom value when provided`() {
        val telemetryService = mockk<TelemetryService>(relaxed = true)
        mockkObject(TelemetryService)
        every { TelemetryService.getInstance() } returns telemetryService

        val builderCaptor = slot<MetricEvent.Builder.() -> Unit>()
        every { telemetryService.record(project, capture(builderCaptor)) } returns Unit

        val event = mapOf(
            "name" to "test_event",
            "value" to 2.5,
            "data" to mapOf(
                "key1" to "value1"
            )
        )

        sut.telemetryEvent(event)

        val builder = DefaultMetricEvent.builder()
        builderCaptor.captured.invoke(builder)

        val metricEvent = builder.build()
        val datum = metricEvent.data.first()

        assertThat(datum.value).isEqualTo(2.5)
        assertThat(datum.metadata).contains(entry("key1", "value1"))
    }

    @Test
    fun `telemetryEvent uses custom passive value when provided`() {
        val telemetryService = mockk<TelemetryService>(relaxed = true)
        mockkObject(TelemetryService)
        every { TelemetryService.getInstance() } returns telemetryService

        val builderCaptor = slot<MetricEvent.Builder.() -> Unit>()
        every { telemetryService.record(project, capture(builderCaptor)) } returns Unit

        val event = mapOf(
            "name" to "test_event",
            "passive" to true,
            "data" to mapOf(
                "key1" to "value1"
            )
        )

        sut.telemetryEvent(event)

        val builder = DefaultMetricEvent.builder()
        builderCaptor.captured.invoke(builder)

        val metricEvent = builder.build()
        val datum = metricEvent.data.first()

        assertThat(datum.passive).isTrue()
        assertThat(datum.metadata).contains(entry("key1", "value1"))
    }

    @Test
    fun `telemetryEvent ignores event without name`() {
        val telemetryService = mockk<TelemetryService>(relaxed = true)
        mockkObject(TelemetryService)
        every { TelemetryService.getInstance() } returns telemetryService

        val event = mapOf(
            "data" to mapOf(
                "key1" to "value1"
            )
        )

        sut.telemetryEvent(event)

        verify(exactly = 0) {
            telemetryService.record(project, any())
        }
    }

    @Test
    fun `telemetryEvent ignores event without data`() {
        val telemetryService = mockk<TelemetryService>(relaxed = true)
        mockkObject(TelemetryService)
        every { TelemetryService.getInstance() } returns telemetryService

        val event = mapOf(
            "name" to "test_event"
        )

        sut.telemetryEvent(event)

        verify(exactly = 0) {
            telemetryService.record(project, any())
        }
    }

    @Test
    fun `telemetryEvent uses default values when not provided`() {
        val telemetryService = mockk<TelemetryService>(relaxed = true)
        mockkObject(TelemetryService)
        every { TelemetryService.getInstance() } returns telemetryService

        val builderCaptor = slot<MetricEvent.Builder.() -> Unit>()
        every { telemetryService.record(project, capture(builderCaptor)) } returns Unit

        val event = mapOf(
            "name" to "test_event",
            "data" to mapOf(
                "key1" to "value1"
            )
        )

        sut.telemetryEvent(event)

        val builder = DefaultMetricEvent.builder()
        builderCaptor.captured.invoke(builder)

        val metricEvent = builder.build()
        val datum = metricEvent.data.first()

        assertThat(datum.unit).isEqualTo(MetricUnit.NONE)
        assertThat(datum.value).isEqualTo(1.0)
        assertThat(datum.passive).isFalse()
        assertThat(datum.metadata).contains(entry("key1", "value1"))
    }

    @Test
    fun `test GSON deserialization behavior for telemetryEvent`() {
        val gson = GsonBuilder()
            .setObjectToNumberStrategy(ToNumberPolicy.LONG_OR_DOUBLE)
            .create()

        val jsonString = """
        {
            "name": "test_event",
            "value": 3.0,
            "passive": true,
            "unit": "Milliseconds",
            "data": {
                "key1": "value1"
            }
        }
        """.trimIndent()

        val result = gson.fromJson(jsonString, Map::class.java)

        val telemetryService = mockk<TelemetryService>(relaxed = true)
        mockkObject(TelemetryService)
        every { TelemetryService.getInstance() } returns telemetryService

        val builderCaptor = slot<MetricEvent.Builder.() -> Unit>()
        every { telemetryService.record(project, capture(builderCaptor)) } returns Unit

        sut.telemetryEvent(result)

        val builder = DefaultMetricEvent.builder()
        builderCaptor.captured.invoke(builder)

        val metricEvent = builder.build()
        val datum = metricEvent.data.first()

        assertThat(datum.passive).isTrue()
        assertThat(datum.unit).isEqualTo(MetricUnit.MILLISECONDS)
        assertThat(datum.value).isEqualTo(3.0)
        assertThat(datum.metadata).contains(entry("key1", "value1"))
    }

    @Test
    fun `getConnectionMetadata returns connection metadata with start URL for bearer token connection`() {
        val mockConnectionManager = mockk<ToolkitConnectionManager>()
        every { project.service<ToolkitConnectionManager>() } returns mockConnectionManager

        val expectedStartUrl = "https://test.aws.com"
        val mockConnection = mockk<AwsBearerTokenConnection> {
            every { startUrl } returns expectedStartUrl
        }

        every { mockConnectionManager.activeConnectionForFeature(QConnection.getInstance()) } returns mockConnection

        assertThat(sut.getConnectionMetadata().get())
            .isEqualTo(ConnectionMetadata(SsoProfileData(expectedStartUrl)))
    }

    @Test
    fun `getConnectionMetadata returns empty start URL when no active connection`() {
        val mockConnectionManager = mockk<ToolkitConnectionManager>()
        every { project.service<ToolkitConnectionManager>() } returns mockConnectionManager

        every { mockConnectionManager.activeConnectionForFeature(QConnection.getInstance()) } returns null

        assertThat(sut.getConnectionMetadata().get())
            .isEqualTo(ConnectionMetadata(SsoProfileData(AmazonQLspConstants.AWS_BUILDER_ID_URL)))
    }

    @Test
    fun `configuration null if no attributes requested`() {
        assertThat(sut.configuration(configurationParams()).get()).isNull()
    }

    @Test
    fun `configuration for codeWhisperer respects opt-out`() {
        CodeWhispererSettings.getInstance().toggleMetricOptIn(false)
        assertThat(sut.configuration(configurationParams("aws.codeWhisperer")).get())
            .singleElement()
            .isEqualTo(
                CodeWhispererLspConfiguration(
                    shouldShareData = false,
                    shouldShareCodeReferences = false
                )
            )
    }

    @Test
    fun `configuration for codeWhisperer respects opt-in`() {
        CodeWhispererSettings.getInstance().toggleMetricOptIn(true)
        assertThat(sut.configuration(configurationParams("aws.codeWhisperer")).get())
            .singleElement()
            .isEqualTo(
                CodeWhispererLspConfiguration(
                    shouldShareData = true,
                    shouldShareCodeReferences = false
                )
            )
    }

    @Test
    fun `configuration empty if attributes unknown`() {
        CodeWhispererSettings.getInstance().toggleMetricOptIn(true)
        assertThat(sut.configuration(configurationParams("something random")).get()).isEmpty()
    }

    @Test
    fun `Gson serializes CodeWhispererLspConfiguration serializes correctly`() {
        val sut = CodeWhispererLspConfiguration(
            shouldShareData = true,
            shouldShareCodeReferences = true
        )
        assertThat(Gson().toJson(sut)).isEqualToIgnoringWhitespace(
            """
                {
                    "shareCodeWhispererContentWithAWS": true,
                    "includeSuggestionsWithCodeReferences": true
                }
            """.trimIndent()
        )
    }

    private fun configurationParams(vararg attributes: String) = ConfigurationParams(
        attributes.map {
            ConfigurationItem().apply {
                section = it
            }
        }
    )
}
