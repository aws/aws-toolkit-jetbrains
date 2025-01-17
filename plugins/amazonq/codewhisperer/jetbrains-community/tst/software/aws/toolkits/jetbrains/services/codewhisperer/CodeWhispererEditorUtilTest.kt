// Copyright 2022 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.codewhisperer

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.Project
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.intellij.testFramework.runInEdtAndGet
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import software.aws.toolkits.jetbrains.services.codewhisperer.CodeWhispererTestUtil.leftContext_success_Iac
import software.aws.toolkits.jetbrains.services.codewhisperer.CodeWhispererTestUtil.pythonFileName
import software.aws.toolkits.jetbrains.services.codewhisperer.CodeWhispererTestUtil.pythonTestLeftContext
import software.aws.toolkits.jetbrains.services.codewhisperer.editor.CodeWhispererEditorUtil
import software.aws.toolkits.jetbrains.services.codewhisperer.language.languages.CodeWhispererPython
import software.aws.toolkits.jetbrains.utils.rules.PythonCodeInsightTestFixtureRule

class CodeWhispererEditorUtilTest {
    @Rule
    @JvmField
    var projectRule = PythonCodeInsightTestFixtureRule()

    private lateinit var project: Project
    private lateinit var fixture: CodeInsightTestFixture

    @Before
    fun setup() {
        project = projectRule.project
        fixture = projectRule.fixture
    }

    @Test
    fun `test getFileContextInfo`() {
        val psiFile = fixture.configureByText(pythonFileName, pythonTestLeftContext)
        val fileContext = runInEdtAndGet {
            fixture.editor.caretModel.moveToOffset(fixture.editor.document.textLength)
            CodeWhispererEditorUtil.getFileContextInfo(fixture.editor, psiFile)
        }

        assertThat(fileContext.filename).isEqualTo(pythonFileName)
        assertThat(fileContext.programmingLanguage).isInstanceOf(CodeWhispererPython::class.java)
        assertThat(fileContext.caretContext.leftFileContext).isEqualTo(pythonTestLeftContext)
        assertThat(fileContext.caretContext.rightFileContext).isEqualTo("")
        assertThat(fileContext.fileRelativePath).isEqualTo("test.py")
    }

    /**
     * # function to add 2 numbers
     * def addTwoNumbers(a, b)
     *                  ^
     *                  cursor
     */
    @Test
    fun `test extractCaretContext`() {
        val pythonComment = "# function to add 2 numbers\n"
        val pythonTestRightContext = "(a, b)"
        fixture.configureByText(pythonFileName, pythonComment)
        val caretContext = runInEdtAndGet {
            WriteCommandAction.runWriteCommandAction(project) {
                fixture.editor.caretModel.moveToOffset(fixture.editor.document.textLength)
                fixture.editor.document.insertString(fixture.editor.caretModel.offset, pythonTestLeftContext)

                fixture.editor.caretModel.moveToOffset(fixture.editor.document.textLength)
                fixture.editor.document.insertString(fixture.editor.caretModel.offset, pythonTestRightContext)
            }

            CodeWhispererEditorUtil.extractCaretContext(fixture.editor)
        }

        assertThat(caretContext.leftFileContext).isEqualTo("$pythonComment$pythonTestLeftContext")
        assertThat(caretContext.rightFileContext).isEqualTo(pythonTestRightContext)
        assertThat(caretContext.leftContextOnCurrentLine).isEqualTo(pythonTestLeftContext)
    }

    @Test
    fun `test for keyword check for json`() {
        val result = CodeWhispererEditorUtil.isSupportedJsonFormat("foo.json", leftContext_success_Iac)
        assertThat(result).isEqualTo(true)
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "app.json",
            "appsettings.json",
            "bower.json",
            "composer.json",
            "db.json",
            "manifest.json",
            "package.json",
            "schema.json",
            "settings.json",
            "tsconfig.json",
            "vcpkg.json"
        ]
    )
    fun `isSupportedJsonFormat should return true by file name`(fileName: String) {
        val result = CodeWhispererEditorUtil.isSupportedJsonFormat(fileName, "")
        assertThat(result).isEqualTo(true)
    }

    @Test
    fun `isSupportedJsonFormat should return false due to no match`() {
        val result = CodeWhispererEditorUtil.isSupportedJsonFormat("foo.json", "")
        assertThat(result).isEqualTo(false)
    }
}
