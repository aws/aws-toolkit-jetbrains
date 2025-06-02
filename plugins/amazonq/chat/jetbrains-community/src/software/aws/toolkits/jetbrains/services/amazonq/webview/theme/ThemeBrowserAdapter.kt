// Copyright 2023 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonq.webview.theme

import kotlinx.coroutines.CompletableDeferred
import org.cef.browser.CefBrowser
import java.awt.Color
import java.awt.Font

// The className must match what's in the mynah-ui package.
const val DARK_MODE_CLASS = "vscode-dark"

/**
 * Takes a [AmazonQTheme] instance and uses it to update CSS variables in the Webview UI.
 */
class ThemeBrowserAdapter {
    fun updateLoginThemeInBrowser(browser: CefBrowser, theme: AmazonQTheme) {
        browser.executeJavaScript("window.changeTheme(${theme.darkMode})", browser.url, 0)
    }

    suspend fun updateThemeInBrowser(browser: CefBrowser, theme: AmazonQTheme, uiReady: CompletableDeferred<Boolean>) {
        uiReady.await()
        val codeToUpdateTheme = buildJsCodeToUpdateTheme(theme)
        browser.executeJavaScript(codeToUpdateTheme, browser.url, 0)
    }

    private fun buildJsCodeToUpdateTheme(theme: AmazonQTheme) = buildString {
        val (bg, altBg, inputBg) = determineInputAndBgColor(theme)
        appendDarkMode(theme.darkMode)

        append("{\n")
        append("const rootElement = document.querySelector(':root');\n")

        append(CssVariable.FontSize, theme.font.toCssSize())
        append(CssVariable.FontFamily, theme.font.toCssFontFamily())

        append(CssVariable.TextColorDefault, theme.defaultText)
        append(CssVariable.TextColorAlt, theme.defaultText)
        append(CssVariable.TextColorStrong, theme.textFieldForeground)
        append(CssVariable.TextColorInput, theme.textFieldForeground)
        append(CssVariable.TextColorLink, theme.linkText)
        append(CssVariable.TextColorWeak, theme.emptyText)
        append(CssVariable.TextColorLight, theme.emptyText)
        append(CssVariable.TextColorDisabled, theme.inactiveText)

        append(CssVariable.Background, bg)
        append(CssVariable.BackgroundAlt, altBg)
        append(CssVariable.CardBackground, bg)
        append(CssVariable.CardBackgroundAlt, altBg)
        append(CssVariable.BorderDefault, theme.border)
        append(CssVariable.BorderFocused, theme.inputBorderFocused)
        append(CssVariable.BorderUnfocused, theme.inputBorderUnfocused)
        append(CssVariable.TabActive, theme.activeTab)

        append(CssVariable.InputBackground, inputBg)

        append(CssVariable.ButtonBackground, theme.buttonBackground)
        append(CssVariable.ButtonForeground, theme.buttonForeground)
        append(CssVariable.SecondaryButtonBackground, theme.secondaryButtonBackground)
        append(CssVariable.SecondaryButtonForeground, theme.secondaryButtonForeground)

        append(CssVariable.StatusInfo, theme.info)
        append(CssVariable.StatusSuccess, theme.success)
        append(CssVariable.StatusWarning, theme.warning)
        append(CssVariable.StatusError, theme.error)

        append(CssVariable.ColorDeep, theme.checkboxBackground)
        append(CssVariable.ColorDeepReverse, theme.checkboxForeground)

        append(CssVariable.SyntaxCodeFontFamily, theme.editorFont.toCssFontFamily("monospace"))
        append(CssVariable.SyntaxCodeFontSize, theme.editorFont.toCssSize())
        append(CssVariable.SyntaxCode, theme.editorForeground)
        append(CssVariable.SyntaxBackground, theme.editorBackground)
        append(CssVariable.SyntaxVariable, theme.editorVariable)
        append(CssVariable.SyntaxOperator, theme.editorOperator)
        append(CssVariable.SyntaxFunction, theme.editorFunction)
        append(CssVariable.SyntaxComment, theme.editorComment)
        append(CssVariable.SyntaxAttributeValue, theme.editorKeyword)
        append(CssVariable.SyntaxAttribute, theme.editorString)
        append(CssVariable.SyntaxProperty, theme.editorProperty)
        append(CssVariable.SyntaxKeyword, theme.editorKeyword)
        append(CssVariable.SyntaxString, theme.editorString)
        append(CssVariable.SyntaxClassName, theme.editorClassName)

        append(CssVariable.MainBackground, theme.buttonBackground)
        append(CssVariable.MainForeground, theme.buttonForeground)

        append("}")
    }

    private fun StringBuilder.append(variable: CssVariable, value: Color) = append(variable, value.toCss())

    private fun StringBuilder.append(variable: CssVariable, value: String) {
        append("rootElement.style.setProperty('")
        append(variable.varName)
        append("', '")
        append(value)
        append("');\n")
    }

    private fun StringBuilder.appendDarkMode(isDarkMode: Boolean) {
        if (isDarkMode) {
            // classList acts as a set, so we don't need to worry about calling add multiple times
            append("document.body.classList.add('$DARK_MODE_CLASS');\n")
        } else {
            append("document.body.classList.remove('$DARK_MODE_CLASS');\n")
        }
    }

    private fun Color.toCss() = "rgba($red,$green,$blue,$alpha)"

    private fun Font.toCssSize() = "${size}px"

    // Some font names have characters that require them to be wrapped in quotes in the CSS variable, for example if they have spaces or a period.
    private fun Font.toCssFontFamily(fallback: String = "system-ui") = "\"$family\", $fallback"

    // darkest = bg, second darkest is alt bg, lightest is input bg
    private fun determineInputAndBgColor(theme: AmazonQTheme): Triple<Color, Color, Color> {
        val colors = arrayOf(theme.editorBackground, theme.background, theme.textFieldBackground).sortedWith(
            Comparator.comparing {
                // luma calculation for brightness
                (0.2126 * it.red) + (0.7152 * it.green) + (0.0722 * it.blue)
            }
        )
        return Triple(colors[0], colors[1], colors[2])
    }
}
