// Copyright 2023 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonq.webview.theme

import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.cef.browser.CefBrowser
import software.aws.toolkits.jetbrains.utils.containsEntries
import java.awt.Color
import kotlin.test.Test

class ThemeBrowserAdapterTest {

    private val color1 = Color(1, 2, 3)
    private val color2 = Color(2, 3, 4)
    private val color3 = Color(3, 4, 5)
    private val color4 = Color(4, 5, 6)
    private val color5 = Color(5, 6, 7)
    private val color6 = Color(6, 7, 8)
    private val color7 = Color(7, 8, 9)
    private val color8 = Color(8, 9, 10)
    private val color9 = Color(9, 10, 11)

    private val javascriptCssPropertyRegex = """rootElement.style.setProperty\('([^']*)', '([^']*)'\);\n""".toRegex()

    private val adapter = ThemeBrowserAdapter()
    private val cefBrowser = mockk<CefBrowser> {
        every { url } returns "url"
        every { executeJavaScript(any(), "url", any()) } just Runs
    }

    @Test
    fun `sets dark mode true`() {
        val theme = mockk<AmazonQTheme>(relaxed = true) {
            every { darkMode } returns true
        }
        adapter.updateThemeInBrowser(cefBrowser, theme)
        assertThat(darkModeClassAdded()).isTrue()
        assertThat(darkModeClassRemoved()).isFalse()
    }

    @Test
    fun `sets dark mode false`() {
        val theme = mockk<AmazonQTheme>(relaxed = true) {
            every { darkMode } returns false
        }
        adapter.updateThemeInBrowser(cefBrowser, theme)
        assertThat(darkModeClassAdded()).isFalse()
        assertThat(darkModeClassRemoved()).isTrue()
    }

    @Test
    fun `sets expected font variables`() {
        val theme = mockk<AmazonQTheme>(relaxed = true) {
            every { font } returns mockk {
                every { family } returns "test-font-family"
                every { size } returns 16
            }
            every { editorFont } returns mockk {
                every { family } returns "test-editor-font-family"
                every { size } returns 18
            }
        }
        adapter.updateThemeInBrowser(cefBrowser, theme)
        assertThat(getCssPropertiesSetInJavascript()).containsEntries(
            CssVariable.FontSize.varName to "16px",
            CssVariable.FontFamily.varName to "\"test-font-family\", system-ui",
            CssVariable.SyntaxCodeFontFamily.varName to "\"test-editor-font-family\", monospace",
            CssVariable.SyntaxCodeFontSize.varName to "18px",
        )
    }

    @Test
    fun `sets expected text colors`() {
        val theme = mockk<AmazonQTheme>(relaxed = true) {
            every { defaultText } returns color1
            every { textFieldForeground } returns color2
            every { linkText } returns color3
            every { inactiveText } returns color4
        }
        adapter.updateThemeInBrowser(cefBrowser, theme)
        assertThat(getCssPropertiesSetInJavascript()).containsEntries(
            CssVariable.TextColorDefault.varName to color1.rgba(),
            CssVariable.TextColorInput.varName to color2.rgba(),
            CssVariable.TextColorStrong.varName to color2.rgba(),
            CssVariable.TextColorLink.varName to color3.rgba(),
            CssVariable.TextColorWeak.varName to color4.rgba(),
        )
    }

    @Test
    fun `sets expected code highlighting colors`() {
        val theme = mockk<AmazonQTheme>(relaxed = true) {
            every { editorBackground } returns color1
            every { editorComment } returns color2
            every { editorForeground } returns color3
            every { editorFunction } returns color4
            every { editorKeyword } returns color5
            every { editorOperator } returns color6
            every { editorProperty } returns color7
            every { editorString } returns color8
            every { editorVariable } returns color9
        }
        adapter.updateThemeInBrowser(cefBrowser, theme)
        assertThat(getCssPropertiesSetInJavascript()).containsEntries(
            CssVariable.SyntaxBackground.varName to color1.rgba(),
            CssVariable.SyntaxComment.varName to color2.rgba(),
            CssVariable.SyntaxCode.varName to color3.rgba(),
            CssVariable.SyntaxFunction.varName to color4.rgba(),
            CssVariable.SyntaxAttributeValue.varName to color5.rgba(),
            CssVariable.SyntaxOperator.varName to color6.rgba(),
            CssVariable.SyntaxProperty.varName to color7.rgba(),
            CssVariable.SyntaxAttribute.varName to color8.rgba(),
            CssVariable.SyntaxVariable.varName to color9.rgba(),
        )
    }

    @Test
    fun `sets expected background and border colors`() {
        val theme = mockk<AmazonQTheme>(relaxed = true) {
            every { background } returns color1
            every { cardBackground } returns color2
            every { border } returns color3
            every { activeTab } returns color4
            every { buttonBackground } returns color5
            every { buttonForeground } returns color6
            every { checkboxBackground } returns color7
            every { checkboxForeground } returns color8
        }
        adapter.updateThemeInBrowser(cefBrowser, theme)
        assertThat(getCssPropertiesSetInJavascript()).containsEntries(
            CssVariable.Background.varName to color1.rgba(),
            CssVariable.BackgroundAlt.varName to color1.rgba(),
            CssVariable.CardBackground.varName to color2.rgba(),
            CssVariable.BorderDefault.varName to color3.rgba(),
            CssVariable.TabActive.varName to color4.rgba(),
            CssVariable.MainBackground.varName to color5.rgba(),
            CssVariable.MainForeground.varName to color6.rgba(),
            CssVariable.ColorDeep.varName to color7.rgba(),
            CssVariable.ColorDeepReverse.varName to color8.rgba(),
        )
    }

    @Test
    fun `sets expected input and button colors`() {
        val theme = mockk<AmazonQTheme>(relaxed = true) {
            every { textFieldBackground } returns color1
            every { buttonBackground } returns color2
            every { buttonForeground } returns color3
            every { secondaryButtonBackground } returns color4
            every { secondaryButtonForeground } returns color5
        }
        adapter.updateThemeInBrowser(cefBrowser, theme)
        assertThat(getCssPropertiesSetInJavascript()).containsEntries(
            CssVariable.InputBackground.varName to color1.rgba(),
            CssVariable.ButtonBackground.varName to color2.rgba(),
            CssVariable.ButtonForeground.varName to color3.rgba(),
            CssVariable.SecondaryButtonBackground.varName to color4.rgba(),
            CssVariable.SecondaryButtonForeground.varName to color5.rgba(),
        )
    }

    @Test
    fun `sets expected status colors`() {
        val theme = mockk<AmazonQTheme>(relaxed = true) {
            every { info } returns color1
            every { success } returns color2
            every { warning } returns color3
            every { error } returns color4
        }
        adapter.updateThemeInBrowser(cefBrowser, theme)
        assertThat(getCssPropertiesSetInJavascript()).containsEntries(
            CssVariable.StatusInfo.varName to color1.rgba(),
            CssVariable.StatusSuccess.varName to color2.rgba(),
            CssVariable.StatusWarning.varName to color3.rgba(),
            CssVariable.StatusError.varName to color4.rgba(),
        )
    }

    private fun getCssPropertiesSetInJavascript() = javascriptCssPropertyRegex.findAll(getJavascriptCodeExecuted()).associate {
        it.groups[1]!!.value to it.groups[2]!!.value
    }

    private fun darkModeClassAdded() = getJavascriptCodeExecuted().contains("document.body.classList.add('vscode-dark');\n")
    private fun darkModeClassRemoved() = getJavascriptCodeExecuted().contains("document.body.classList.remove('vscode-dark');\n")

    private fun Color.rgba() = "rgba($red,$green,$blue,$alpha)"

    private fun getJavascriptCodeExecuted(): String {
        val slot = slot<String>()
        verify { cefBrowser.executeJavaScript(capture(slot), any(), any()) }
        return slot.captured
    }
}
