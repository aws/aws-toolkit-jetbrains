// Copyright 2023 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.codewhisperer.languages

import org.assertj.core.api.Assertions
import org.junit.Before
import org.junit.Test
import software.aws.toolkits.jetbrains.services.codewhisperer.CodeWhispererFileCrawlerTest
import software.aws.toolkits.jetbrains.services.codewhisperer.util.CodeWhispererFileCrawler
import software.aws.toolkits.jetbrains.services.codewhisperer.util.JavascriptCodeWhispererFileCrawler
import software.aws.toolkits.jetbrains.utils.rules.CodeInsightTestFixtureRule

class JavascriptCodeWhispererFileCrawlerTest : CodeWhispererFileCrawlerTest(CodeInsightTestFixtureRule()) {
    lateinit var sut: CodeWhispererFileCrawler

    @Before
    override fun setup() {
        super.setup()
        sut = JavascriptCodeWhispererFileCrawler()
    }

    @Test
    fun `isTest - should return false`() {
        val file1 = fixture.addFileToProject("src/utils/foo.js", "")
        Assertions.assertThat(sut.isTestFile(file1.virtualFile, project)).isFalse

        val file2 = fixture.addFileToProject("src/controler/bar.jsx", "")
        Assertions.assertThat(sut.isTestFile(file2.virtualFile, project)).isFalse

        val file3 = fixture.addFileToProject("main.js", "")
        Assertions.assertThat(sut.isTestFile(file3.virtualFile, project)).isFalse

        val file4 = fixture.addFileToProject("component/dto/boo.jsx", "")
        Assertions.assertThat(sut.isTestFile(file4.virtualFile, project)).isFalse
    }

    @Test
    fun `isTest - should return true`() {
        val file1 = fixture.addFileToProject("tst/components/foo.test.js", "")
        Assertions.assertThat(sut.isTestFile(file1.virtualFile, project)).isTrue

        val file2 = fixture.addFileToProject("test/components/foo.spec.js", "")
        Assertions.assertThat(sut.isTestFile(file2.virtualFile, project)).isTrue

        val file3 = fixture.addFileToProject("tests/components/foo.test.jsx", "")
        Assertions.assertThat(sut.isTestFile(file3.virtualFile, project)).isTrue

        val file4 = fixture.addFileToProject("foo.spec.jsx", "")
        Assertions.assertThat(sut.isTestFile(file4.virtualFile, project)).isTrue

        val file5 = fixture.addFileToProject("foo.test.js", "")
        Assertions.assertThat(sut.isTestFile(file5.virtualFile, project)).isTrue

        val file6 = fixture.addFileToProject("src/tst/services/fooService.test.js", "")
        Assertions.assertThat(sut.isTestFile(file6.virtualFile, project)).isTrue

        val file7 = fixture.addFileToProject("tests/services/barService.spec.jsx", "")
        Assertions.assertThat(sut.isTestFile(file7.virtualFile, project)).isTrue

        val file8 = fixture.addFileToProject("foo.Test.js", "")
        Assertions.assertThat(sut.isTestFile(file8.virtualFile, project)).isTrue

        val file9 = fixture.addFileToProject("foo.Spec.js", "")
        Assertions.assertThat(sut.isTestFile(file9.virtualFile, project)).isTrue
    }

    @Test
    fun `guessSourceFileName javascript`() {
        Assertions.assertThat(sut.guessSourceFileName("fooBar.test.js")).isEqualTo("fooBar.js")
        Assertions.assertThat(sut.guessSourceFileName("fooBar.spec.js")).isEqualTo("fooBar.js")
        Assertions.assertThat(sut.guessSourceFileName("fooBarNoIdea.js")).isNull()
    }

    @Test
    fun `guessSourceFileName jsx`() {
        Assertions.assertThat(sut.guessSourceFileName("fooBar.test.jsx")).isEqualTo("fooBar.jsx")
        Assertions.assertThat(sut.guessSourceFileName("fooBar.spec.jsx")).isEqualTo("fooBar.jsx")
        Assertions.assertThat(sut.guessSourceFileName("fooBarNoIdea.jsx")).isNull()
    }
}
