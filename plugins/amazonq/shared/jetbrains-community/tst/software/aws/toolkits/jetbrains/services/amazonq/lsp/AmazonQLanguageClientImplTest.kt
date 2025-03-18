// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0
@file:Suppress("BannedImports")

package software.aws.toolkits.jetbrains.services.amazonq.lsp

import com.google.gson.Gson
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.testFramework.ApplicationExtension
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.eclipse.lsp4j.ConfigurationItem
import org.eclipse.lsp4j.ConfigurationParams
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import software.aws.toolkits.jetbrains.core.credentials.AwsBearerTokenConnection
import software.aws.toolkits.jetbrains.core.credentials.ToolkitConnectionManager
import software.aws.toolkits.jetbrains.core.credentials.pinning.QConnection
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.credentials.ConnectionMetadata
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.credentials.SsoProfileData
import software.aws.toolkits.jetbrains.settings.CodeWhispererSettings

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
