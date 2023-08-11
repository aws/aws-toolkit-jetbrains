// Copyright 2023 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.codewhisperer.language

import com.intellij.openapi.project.Project
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import software.aws.toolkits.jetbrains.services.codewhisperer.language.filecrawler.TypescriptCodeWhispererFileCrawler
import software.aws.toolkits.jetbrains.services.codewhisperer.util.CodeWhispererFileCrawler
import software.aws.toolkits.jetbrains.utils.rules.CodeInsightTestFixtureRule

class TypescriptCodeWhispererFileCrawlerTest {
    @JvmField
    @Rule
    val projectRule: CodeInsightTestFixtureRule = CodeInsightTestFixtureRule()

    lateinit var fixture: CodeInsightTestFixture
    lateinit var project: Project

    lateinit var sut: CodeWhispererFileCrawler

    @Before
    fun setup() {
        sut = TypescriptCodeWhispererFileCrawler()

        project = projectRule.project
        fixture = projectRule.fixture
    }

    @Test
    fun `isTest - should return false`() {
        val file1 = fixture.addFileToProject("src/utils/foo.ts", "")
        assertThat(sut.isTestFile(file1.virtualFile, project)).isFalse

        val file2 = fixture.addFileToProject("src/controler/bar.tsx", "")
        assertThat(sut.isTestFile(file2.virtualFile, project)).isFalse

        val file3 = fixture.addFileToProject("main.ts", "")
        assertThat(sut.isTestFile(file3.virtualFile, project)).isFalse

        val file4 = fixture.addFileToProject("component/dto/boo.tsx", "")
        assertThat(sut.isTestFile(file4.virtualFile, project)).isFalse
    }

    @Test
    fun `isTest - should return true`() {
        val file1 = fixture.addFileToProject("tst/components/foo.test.ts", "")
        assertThat(sut.isTestFile(file1.virtualFile, project)).isTrue

        val file2 = fixture.addFileToProject("test/components/foo.spec.ts", "")
        assertThat(sut.isTestFile(file2.virtualFile, project)).isTrue

        val file3 = fixture.addFileToProject("tests/components/foo.test.tsx", "")
        assertThat(sut.isTestFile(file3.virtualFile, project)).isTrue

        val file4 = fixture.addFileToProject("foo.spec.tsx", "")
        assertThat(sut.isTestFile(file4.virtualFile, project)).isTrue

        val file5 = fixture.addFileToProject("foo.test.ts", "")
        assertThat(sut.isTestFile(file5.virtualFile, project)).isTrue

        val file6 = fixture.addFileToProject("src/tst/services/fooService.test.ts", "")
        assertThat(sut.isTestFile(file6.virtualFile, project)).isTrue

        val file7 = fixture.addFileToProject("tests/services/barService.spec.tsx", "")
        assertThat(sut.isTestFile(file7.virtualFile, project)).isTrue

        val file8 = fixture.addFileToProject("foo.Test.ts", "")
        assertThat(sut.isTestFile(file8.virtualFile, project)).isTrue

        val file9 = fixture.addFileToProject("foo.Spec.ts", "")
        assertThat(sut.isTestFile(file9.virtualFile, project)).isTrue
    }

    @Test
    fun `guessSourceFileName typescript`() {
        assertThat(sut.guessSourceFileName("fooBar.test.ts")).isEqualTo("fooBar.ts")
        assertThat(sut.guessSourceFileName("fooBar.spec.ts")).isEqualTo("fooBar.ts")
        assertThat(sut.guessSourceFileName("fooBarNoIdea.ts")).isNull()
    }

    @Test
    fun `guessSourceFileName tsx`() {
        assertThat(sut.guessSourceFileName("fooBar.test.tsx")).isEqualTo("fooBar.tsx")
        assertThat(sut.guessSourceFileName("fooBar.spec.tsx")).isEqualTo("fooBar.tsx")
        assertThat(sut.guessSourceFileName("fooBarNoIdea.tsx")).isNull()
    }
}
