// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.settings

import com.intellij.util.xmlb.XmlSerializer
import org.assertj.core.api.Assertions.assertThat
import org.jdom.output.XMLOutputter
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import software.aws.toolkits.jetbrains.utils.xmlElement

class LspSettingsTest {
    private lateinit var lspSettings: LspSettings

    @BeforeEach
    fun setUp() {
        lspSettings = LspSettings()
        lspSettings.loadState(LspConfiguration())
    }

    @Test
    fun `artifact path is empty by default`() {
        assertThat(lspSettings.getArtifactPath()).isEmpty()
    }

    @Test
    fun `artifact path can be set`() {
        lspSettings.setArtifactPath("test\\lsp.js")
        assertThat(lspSettings.getArtifactPath()).isNotEmpty()
        assertThat(lspSettings.getArtifactPath()).isEqualTo("test\\lsp.js")
    }

    @Test
    fun `artifact path cannot be null`() {
        lspSettings.setArtifactPath(null)
        assertThat(lspSettings.getArtifactPath()).isEmpty()
    }

    @Test
    fun `serialize settings to ensure backwards compatibility`() {
        val element = xmlElement(
            """
            <component name="LspSettings">
  </component>
            """.trimIndent()
        )
        lspSettings.setArtifactPath("temp\\lsp.js")

        XmlSerializer.serializeInto(lspSettings.state, element)

        val actual = XMLOutputter().outputString(element)

        val expected = "<component name=\"LspSettings\">\n" +
            "<option name=\"artifactPath\" value=\"temp\\lsp.js\" /></component>"

        assertThat(actual).isEqualTo(expected)
    }

    @Test
    fun `deserialize empty settings to ensure backwards compatibility`() {
        val element = xmlElement(
            """
                <component name="LspSettings">
                </component>
                """
        )
        val actual = XmlSerializer.deserialize(element, LspConfiguration::class.java)
        assertThat(actual.artifactPath).isEmpty()
    }

    @Test
    fun `deserialize existing settings to ensure backwards compatibility`() {
        val element = xmlElement(
            """
                <component name="LspSettings">
                    <option name="artifactPath" value='temp\lsp.js'/>
                </component>
            """.trimIndent()
        )
        val actual = XmlSerializer.deserialize(element, LspConfiguration::class.java)
        assertThat(actual.artifactPath).isNotEmpty()
        assertThat(actual.artifactPath).isEqualTo("temp\\lsp.js")
    }
}
