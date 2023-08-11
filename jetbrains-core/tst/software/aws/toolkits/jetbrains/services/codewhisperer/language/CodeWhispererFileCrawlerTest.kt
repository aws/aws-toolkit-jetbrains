// Copyright 2023 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.codewhisperer.language

import com.intellij.openapi.project.Project
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.intellij.testFramework.runInEdtAndWait
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import software.aws.toolkits.jetbrains.services.codewhisperer.language.filecrawler.JavaCodeWhispererFileCrawler
import software.aws.toolkits.jetbrains.services.codewhisperer.language.filecrawler.JavascriptCodeWhispererFileCrawler
import software.aws.toolkits.jetbrains.services.codewhisperer.language.filecrawler.PythonCodeWhispererFileCrawler
import software.aws.toolkits.jetbrains.services.codewhisperer.language.filecrawler.TypescriptCodeWhispererFileCrawler
import software.aws.toolkits.jetbrains.services.codewhisperer.util.CodeWhispererFileCrawler
import software.aws.toolkits.jetbrains.services.codewhisperer.util.content
import software.aws.toolkits.jetbrains.utils.rules.CodeInsightTestFixtureRule

class CodeWhispererFileCrawlerCommonTest {
    @JvmField
    @Rule
    val projectRule: CodeInsightTestFixtureRule = CodeInsightTestFixtureRule()

    lateinit var sut: CodeWhispererFileCrawler

    lateinit var fixture: CodeInsightTestFixture
    lateinit var project: Project

    @Before
    fun setup() {
        fixture = projectRule.fixture
        project = projectRule.project
    }

    @Test
    fun `searchKeywordsInOpenedFile should exclude target file itself and files with different file extension`() {
        val targetFile = fixture.addFileToProject("Foo.java", "I have 10 Foo in total, Foo, Foo, Foo, Foo, Foo, Foo, Foo, Foo, Foo")

        val file0 = fixture.addFileToProject("file0.py", "I have 7 Foo, Foo, Foo, Foo, Foo, Foo, Foo, but I am a pyfile")
        val file1 = fixture.addFileToProject("File1.java", "I have 4 Foo key words : Foo, Foo, Foo")
        val file2 = fixture.addFileToProject("File2.java", "I have 2 Foo Foo")
        val file3 = fixture.addFileToProject("File3.java", "I have only 1 Foo")
        val file4 = fixture.addFileToProject("File4.java", "bar bar bar, i have a lot of bar")

        runInEdtAndWait {
            fixture.openFileInEditor(targetFile.virtualFile)
            fixture.openFileInEditor(file0.virtualFile)
            fixture.openFileInEditor(file1.virtualFile)
            fixture.openFileInEditor(file2.virtualFile)
            fixture.openFileInEditor(file3.virtualFile)
            fixture.openFileInEditor(file4.virtualFile)
        }

        listOf(
            JavaCodeWhispererFileCrawler(),
            PythonCodeWhispererFileCrawler(),
            TypescriptCodeWhispererFileCrawler(),
            JavascriptCodeWhispererFileCrawler()
        ).forEach {
            sut = it

            val result = sut.searchKeywordsInOpenedFile(targetFile) { psiFile ->
                psiFile.virtualFile.content().split(" ")
            }
            assertThat(result).isEqualTo(file1.virtualFile)
        }
    }

    @Test
    fun `searchKeywordsInOpenedFile is language agnostic`() {
        sut = JavaCodeWhispererFileCrawler()

        val targetFile = fixture.addFileToProject("Foo.ts", "I have 10 Foo in total, Foo, Foo, Foo, Foo, Foo, Foo, Foo, Foo, Foo")

        val file0 = fixture.addFileToProject("file0.java", "I have 7 Foo, Foo, Foo, Foo, Foo, Foo, Foo, but I am a pyfile")
        val file1 = fixture.addFileToProject("File1.ts", "I have 4 Foo key words : Foo, Foo, Foo")
        val file2 = fixture.addFileToProject("File2.ts", "I have 2 Foo Foo")
        val file3 = fixture.addFileToProject("File3.ts", "I have only 1 Foo")
        val file4 = fixture.addFileToProject("File4.ts", "bar bar bar, i have a lot of bar")

        runInEdtAndWait {
            fixture.openFileInEditor(targetFile.virtualFile)
            fixture.openFileInEditor(file0.virtualFile)
            fixture.openFileInEditor(file1.virtualFile)
            fixture.openFileInEditor(file2.virtualFile)
            fixture.openFileInEditor(file3.virtualFile)
            fixture.openFileInEditor(file4.virtualFile)
        }

        listOf(
            JavaCodeWhispererFileCrawler(),
            PythonCodeWhispererFileCrawler(),
            TypescriptCodeWhispererFileCrawler(),
            JavascriptCodeWhispererFileCrawler()
        ).forEach {
            sut = it

            val result = sut.searchKeywordsInOpenedFile(targetFile) { psiFile ->
                psiFile.virtualFile.content().split(" ")
            }
            assertThat(result).isEqualTo(file1.virtualFile)
        }
    }
}
