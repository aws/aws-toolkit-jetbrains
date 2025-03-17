// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0
@file:Suppress("BannedImports")

package software.aws.toolkits.jetbrains.services.amazonq.lsp

import com.google.gson.Gson
import com.intellij.testFramework.ApplicationExtension
import org.assertj.core.api.Assertions.assertThat
import org.eclipse.lsp4j.ConfigurationItem
import org.eclipse.lsp4j.ConfigurationParams
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import software.aws.toolkits.jetbrains.settings.CodeWhispererSettings

@ExtendWith(ApplicationExtension::class)
class AmazonQLanguageClientImplTest {
    private val sut = AmazonQLanguageClientImpl()

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
