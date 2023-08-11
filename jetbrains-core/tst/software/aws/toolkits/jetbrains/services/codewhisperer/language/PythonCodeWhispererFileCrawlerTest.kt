// Copyright 2023 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.codewhisperer.language

import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.project.Project
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.intellij.testFramework.runInEdtAndWait
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import software.aws.toolkits.core.utils.test.aString
import software.aws.toolkits.jetbrains.services.codewhisperer.language.filecrawler.PythonCodeWhispererFileCrawler
import software.aws.toolkits.jetbrains.services.codewhisperer.util.CodeWhispererFileCrawler
import software.aws.toolkits.jetbrains.utils.rules.CodeInsightTestFixtureRule
import software.aws.toolkits.jetbrains.utils.rules.PythonCodeInsightTestFixtureRule

class PythonCodeWhispererFileCrawlerTest {
    @JvmField
    @Rule
    val projectRule: CodeInsightTestFixtureRule = PythonCodeInsightTestFixtureRule()

    lateinit var sut: CodeWhispererFileCrawler

    lateinit var project: Project
    lateinit var fixture: CodeInsightTestFixture

    @Before
    fun setup() {
        sut = PythonCodeWhispererFileCrawler()

        project = projectRule.project
        fixture = projectRule.fixture
    }

    @Test
    fun `isTest - should return false`() {
        val file1 = fixture.addFileToProject("src/utils/foo.py", "")
        assertThat(sut.isTestFile(file1.virtualFile, project)).isFalse

        val file2 = fixture.addFileToProject("src/controler/bar.py", "")
        assertThat(sut.isTestFile(file2.virtualFile, project)).isFalse

        val file3 = fixture.addFileToProject("main.py", "")
        assertThat(sut.isTestFile(file3.virtualFile, project)).isFalse

        val file4 = fixture.addFileToProject("component/dto/boo.py", "")
        assertThat(sut.isTestFile(file4.virtualFile, project)).isFalse
    }

    @Test
    fun `isTest - should return true`() {
        val file1 = fixture.addFileToProject("tst/components/foo.py", "")
        assertThat(sut.isTestFile(file1.virtualFile, project)).isTrue

        val file2 = fixture.addFileToProject("test/components/foo.py", "")
        assertThat(sut.isTestFile(file2.virtualFile, project)).isTrue

        val file3 = fixture.addFileToProject("tests/components/foo.py", "")
        assertThat(sut.isTestFile(file3.virtualFile, project)).isTrue

        val file4 = fixture.addFileToProject("foo_test.py", "")
        assertThat(sut.isTestFile(file4.virtualFile, project)).isTrue

        val file5 = fixture.addFileToProject("test_foo.py", "")
        assertThat(sut.isTestFile(file5.virtualFile, project)).isTrue

        val file6 = fixture.addFileToProject("src/tst/services/foo_service_test.py", "")
        assertThat(sut.isTestFile(file6.virtualFile, project)).isTrue

        val file7 = fixture.addFileToProject("tests/services/test_bar_service.py", "")
        assertThat(sut.isTestFile(file7.virtualFile, project)).isTrue
    }

    @Test
    fun `listUtgCandidate by name`() {
        val mainPsi = fixture.addFileToProject("main.py", aString())
        fixture.addFileToProject("another_class.py", aString())
        fixture.addFileToProject("class2.py", aString())
        fixture.addFileToProject("class3.py", aString())
        val tstPsi = fixture.addFileToProject("/test/test_main.py", aString())

        runInEdtAndWait {
            fixture.openFileInEditor(tstPsi.virtualFile)
            val actual = sut.listUtgCandidate(tstPsi)
            assertThat(actual).isNotNull.isEqualTo(mainPsi.virtualFile)
        }
    }

    @Test
    fun `listUtgCandidate by content`() {
        val mainPsi = fixture.addFileToProject(
            "main.py",
            """
            def add(num1, num2):
                return num1 + num2
                
            if __name__ == 'main':
                
            """.trimIndent()
        )
        val file1 = fixture.addFileToProject("Class1.java", "trivial string 1")
        val file2 = fixture.addFileToProject("Class2.java", "trivial string 2")
        val file3 = fixture.addFileToProject("Class3.java", "trivial string 3")
        val tstPsi = fixture.addFileToProject(
            "/test/main_test_not_following_naming_convention.py",
            """
            class TestClass(unittest.TestCase):
                def test_add_numbers(self):
                    result = add(1, 2)
                    self.assertEqual(result, 8, "")
            """.trimIndent()
        )

        runInEdtAndWait {
            fixture.openFileInEditor(mainPsi.virtualFile)
            fixture.openFileInEditor(file1.virtualFile)
            fixture.openFileInEditor(file2.virtualFile)
            fixture.openFileInEditor(file3.virtualFile)
            fixture.openFileInEditor(tstPsi.virtualFile)
        }

        runInEdtAndWait {
            val openedFiles = EditorFactory.getInstance().allEditors.size

            val actual = sut.listUtgCandidate(tstPsi)

            assertThat(openedFiles).isEqualTo(5)
            assertThat(actual).isNotNull.isEqualTo(mainPsi.virtualFile)
        }
    }

    @Test
    fun `guessSourceFileName python`() {
        val sut = PythonCodeWhispererFileCrawler()

        assertThat(sut.guessSourceFileName("test_foo_bar.py")).isEqualTo("foo_bar.py")
        assertThat(sut.guessSourceFileName("test_foo.py")).isEqualTo("foo.py")
        assertThat(sut.guessSourceFileName("foo_test.py")).isEqualTo("foo.py")
        assertThat(sut.guessSourceFileName("foo_test.py")).isEqualTo("foo.py")
        assertThat(sut.guessSourceFileName("foo_bar_no_idea.py")).isNull()
    }
}
