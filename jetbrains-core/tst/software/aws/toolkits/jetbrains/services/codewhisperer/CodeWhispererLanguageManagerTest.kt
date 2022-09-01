// Copyright 2022 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.codewhisperer

import com.intellij.openapi.application.ApplicationManager
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.replaceService
import org.assertj.core.api.Assertions.assertThat
import org.junit.Rule
import org.junit.Test
import org.mockito.internal.verification.Times
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.spy
import org.mockito.kotlin.verify
import software.aws.toolkits.jetbrains.services.codewhisperer.language.CodeWhispererLanguageManager
import software.aws.toolkits.jetbrains.services.codewhisperer.language.toCodeWhispererLanguage
import software.aws.toolkits.jetbrains.services.codewhisperer.language.toProgrammingLanguage
import software.aws.toolkits.jetbrains.services.codewhisperer.model.CaretContext
import software.aws.toolkits.jetbrains.services.codewhisperer.model.FileContextInfo
import software.aws.toolkits.jetbrains.services.codewhisperer.model.ProgrammingLanguage
import software.aws.toolkits.jetbrains.services.codewhisperer.service.CodeWhispererService
import software.aws.toolkits.jetbrains.utils.rules.PythonCodeInsightTestFixtureRule
import software.aws.toolkits.telemetry.CodewhispererLanguage

class CodeWhispererLanguageManagerTest {
    @Rule
    @JvmField
    var projectRule = PythonCodeInsightTestFixtureRule()

    @Rule
    @JvmField
    val disposableRule = DisposableRule()

    @Test
    fun `test toCodeWhispererLanguage`() {
        var codewhispererLanguage = ProgrammingLanguage(CodewhispererLanguage.Java).toCodeWhispererLanguage()
        assertThat(codewhispererLanguage).isEqualTo(CodewhispererLanguage.Java)

        codewhispererLanguage = ProgrammingLanguage(CodewhispererLanguage.Python).toCodeWhispererLanguage()
        assertThat(codewhispererLanguage).isEqualTo(CodewhispererLanguage.Python)

        codewhispererLanguage = ProgrammingLanguage(CodewhispererLanguage.Javascript).toCodeWhispererLanguage()
        assertThat(codewhispererLanguage).isEqualTo(CodewhispererLanguage.Javascript)

        codewhispererLanguage = ProgrammingLanguage("plain_text").toCodeWhispererLanguage()
        assertThat(codewhispererLanguage).isEqualTo(CodewhispererLanguage.Plaintext)

        codewhispererLanguage = ProgrammingLanguage(CodewhispererLanguage.Unknown).toCodeWhispererLanguage()
        assertThat(codewhispererLanguage).isEqualTo(CodewhispererLanguage.Unknown)

        codewhispererLanguage = ProgrammingLanguage("unknown language name").toCodeWhispererLanguage()
        assertThat(codewhispererLanguage).isEqualTo(CodewhispererLanguage.Unknown)
    }

    @Test
    fun `test toProgrammingLanguage`() {
        var programmingLanguage = CodewhispererLanguage.Python.toProgrammingLanguage()
        assertThat(programmingLanguage.languageName).isEqualTo(CodewhispererLanguage.Python.toString())

        programmingLanguage = CodewhispererLanguage.Java.toProgrammingLanguage()
        assertThat(programmingLanguage.languageName).isEqualTo(CodewhispererLanguage.Java.toString())

        programmingLanguage = CodewhispererLanguage.Javascript.toProgrammingLanguage()
        assertThat(programmingLanguage.languageName).isEqualTo(CodewhispererLanguage.Javascript.toString())
    }

    @Test
    fun `test mapProgrammingLanguage`() {
        val java = ProgrammingLanguage("java")
        val python = ProgrammingLanguage("python")
        val javascript = ProgrammingLanguage("javascript")
        val jsx = ProgrammingLanguage("jsx harmony")

        val manager = CodeWhispererLanguageManager()

        var actual = manager.mapProgrammingLanguage(java)
        assertThat(actual).isEqualTo(ProgrammingLanguage("java"))

        actual = manager.mapProgrammingLanguage(python)
        assertThat(actual).isEqualTo(ProgrammingLanguage("python"))

        actual = manager.mapProgrammingLanguage(javascript)
        assertThat(actual).isEqualTo(ProgrammingLanguage("javascript"))

        actual = manager.mapProgrammingLanguage(jsx)
        assertThat(actual).isEqualTo(ProgrammingLanguage("javascript"))
    }

    @Test
    fun `test buildCodeWhispererRequest should call mapProgrammingLanguage`() {
        testMapProgrammingLanguageUtil("test.jsx", "jsx harmony", "javascript")
        testMapProgrammingLanguageUtil("test.py", "python", "python")
        testMapProgrammingLanguageUtil("test.java", "java", "java")
        testMapProgrammingLanguageUtil("test.js", "javascript", "javascript")
    }

    private fun testMapProgrammingLanguageUtil(fileName: String, languageName: String, expectedLanguageName: String) {
        val languageManager = spy(CodeWhispererLanguageManager())
        ApplicationManager.getApplication().replaceService(CodeWhispererLanguageManager::class.java, languageManager, disposableRule.disposable)

        val caretContextMock = mock<CaretContext> {
            on { leftFileContext } doReturn ""
            on { rightFileContext } doReturn ""
        }

        val fileContextInfo = mock<FileContextInfo> {
            on { programmingLanguage } doReturn ProgrammingLanguage(languageName)
            on { caretContext } doReturn caretContextMock
            on { filename } doReturn fileName
        }

        val requestBuilt = CodeWhispererService.buildCodeWhispererRequest(fileContextInfo)

        verify(languageManager, Times(1)).mapProgrammingLanguage(eq(ProgrammingLanguage(languageName)))
        assertThat(requestBuilt.fileContext().programmingLanguage().languageName()).isEqualTo(expectedLanguageName)
    }
}
