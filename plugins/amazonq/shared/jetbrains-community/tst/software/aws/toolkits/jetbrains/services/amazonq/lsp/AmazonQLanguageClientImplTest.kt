// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0
@file:Suppress("BannedImports")

package software.aws.toolkits.jetbrains.services.amazonq.lsp

import com.google.gson.Gson
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.testFramework.ApplicationExtension
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.replaceService
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import migration.software.aws.toolkits.jetbrains.settings.AwsSettings
import org.assertj.core.api.Assertions.assertThat
import org.eclipse.lsp4j.ConfigurationItem
import org.eclipse.lsp4j.ConfigurationParams
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import software.aws.toolkits.core.utils.test.aString
import software.aws.toolkits.jetbrains.core.credentials.AwsBearerTokenConnection
import software.aws.toolkits.jetbrains.core.credentials.ToolkitConnectionManager
import software.aws.toolkits.jetbrains.core.credentials.pinning.QConnection
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.credentials.ConnectionMetadata
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.credentials.SsoProfileData
import software.aws.toolkits.jetbrains.services.codewhisperer.customization.CodeWhispererCustomization
import software.aws.toolkits.jetbrains.services.codewhisperer.customization.CodeWhispererModelConfigurator
import software.aws.toolkits.jetbrains.settings.CodeWhispererSettings
import software.aws.toolkits.jetbrains.settings.CodeWhispererSettings.Companion.CONTEXT_INDEX_SIZE
import software.aws.toolkits.jetbrains.settings.CodeWhispererSettings.Companion.CONTEXT_INDEX_THREADS
import kotlin.random.Random
import kotlin.random.nextInt

@ExtendWith(ApplicationExtension::class)
class AmazonQLanguageClientImplTest {
    private val project: Project = mockk(relaxed = true)
    private val sut = AmazonQLanguageClientImpl(project)

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
        CodeWhispererSettings.getInstance().toggleWorkspaceContextEnabled(true)
        assertThat(sut.configuration(configurationParams("aws.codeWhisperer")).get())
            .singleElement()
            .isEqualTo(
                CodeWhispererLspConfiguration(
                    shouldShareData = false,
                    shouldShareCodeReferences = false,
                    shouldEnableWorkspaceContext = true
                )
            )
    }

    @Test
    fun `configuration for codeWhisperer respects opt-in`() {
        CodeWhispererSettings.getInstance().toggleMetricOptIn(true)
        CodeWhispererSettings.getInstance().toggleWorkspaceContextEnabled(true)
        assertThat(sut.configuration(configurationParams("aws.codeWhisperer")).get())
            .singleElement()
            .isEqualTo(
                CodeWhispererLspConfiguration(
                    shouldShareData = true,
                    shouldShareCodeReferences = false,
                    shouldEnableWorkspaceContext = true
                )
            )
    }

    @Test
    fun `configuration for workspace context respects opt-in`() {
        CodeWhispererSettings.getInstance().toggleWorkspaceContextEnabled(false)
        assertThat(sut.configuration(configurationParams("aws.codeWhisperer")).get())
            .singleElement()
            .isEqualTo(
                CodeWhispererLspConfiguration(
                    shouldShareData = true,
                    shouldShareCodeReferences = false,
                    shouldEnableWorkspaceContext = false
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
            // language=JSON
            """
                {
                    "shareCodeWhispererContentWithAWS": true,
                    "includeSuggestionsWithCodeReferences": true
                }
            """.trimIndent()
        )
    }

    @Test
    fun `configuration for Amazon Q respects IDE settings`(@TestDisposable disposable: Disposable) {
        // Mock ToolkitConnectionManager
        val mockConnectionManager = mockk<ToolkitConnectionManager>()
        every { project.service<ToolkitConnectionManager>() } returns mockConnectionManager

        val telemetryEnabled = Random.nextBoolean()
        val customizationArn = aString()
        val workerThreads = Random.nextInt(CONTEXT_INDEX_THREADS)
        val indexSize = Random.nextInt(CONTEXT_INDEX_SIZE)
        val enableIndexing = Random.nextBoolean()
        val enableGpu = Random.nextBoolean()

        val mockQSettings = mockk<CodeWhispererSettings> {
            every { getProjectContextIndexThreadCount() } returns workerThreads
            every { getProjectContextIndexMaxSize() } returns indexSize
            every { isProjectContextEnabled() } returns enableIndexing
            every { isProjectContextGpu() } returns enableGpu
        }
        mockkObject(CodeWhispererSettings.Companion)
        every { CodeWhispererSettings.getInstance() } returns mockQSettings

        // Mock CodeWhispererModelConfigurator
        val mockConfigurator = mockk<CodeWhispererModelConfigurator> {
            every { activeCustomization(project) } returns CodeWhispererCustomization(
                arn = customizationArn,
                name = "name",
                description = "description",
            )
        }
        ApplicationManager.getApplication().replaceService(CodeWhispererModelConfigurator::class.java, mockConfigurator, disposable)

        // Mock AWS Settings
        AwsSettings.getInstance().isTelemetryEnabled = telemetryEnabled

        assertThat(sut.configuration(configurationParams("aws.q")).get())
            .singleElement()
            .isEqualTo(
                AmazonQLspConfiguration(
                    optOutTelemetry = telemetryEnabled,
                    customization = customizationArn,
                    projectContext = ProjectContextConfiguration(
                        enableLocalIndexing = enableIndexing,
                        enableGpuAcceleration = enableGpu,
                        indexWorkerThreads = workerThreads,
                        localIndexing = LocalIndexingConfiguration(
                            maxIndexSizeMB = indexSize,
                        )
                    )
                )
            )
    }

    @Test
    fun `Gson serializes AmazonQLspConfiguration correctly`() {
        val sut = AmazonQLspConfiguration(
            optOutTelemetry = true,
            enableTelemetryEvents = true,
            customization = "arn",
            projectContext = ProjectContextConfiguration(
                enableLocalIndexing = true,
                enableGpuAcceleration = true,
                indexWorkerThreads = 123,
                localIndexing = LocalIndexingConfiguration(
                    maxFileSizeMB = 789,
                    maxIndexSizeMB = 456,
                    indexCacheDirPath = "/a/path",
                    ignoreFilePatterns = listOf("ignore", "patterns")
                )
            )
        )

        assertThat(Gson().toJson(sut)).isEqualToIgnoringWhitespace(
            // language=JSON
            """
            {
                "optOutTelemetry": true,
                "enableTelemetryEventsToDestination": true,
                "customization": "arn",
                "projectContext": {
                    "enableLocalIndexing": true,
                    "enableGpuAcceleration": true,
                    "indexWorkerThreads": 123,
                    "localIndexing": {
                        "ignoreFilePatterns": [
                            "ignore",
                            "patterns"
                        ],
                        "maxFileSizeMB": 789,
                        "maxIndexSizeMB": 456,
                        "indexCacheDirPath": "/a/path"
                    }
                }
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
