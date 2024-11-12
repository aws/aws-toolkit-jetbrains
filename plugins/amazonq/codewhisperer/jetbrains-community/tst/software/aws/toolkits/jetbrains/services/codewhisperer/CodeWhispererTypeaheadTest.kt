// Copyright 2022 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.codewhisperer

import com.intellij.testFramework.runInEdtAndWait
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import software.aws.toolkits.jetbrains.services.codewhisperer.CodeWhispererTestUtil.pythonFileName
import software.aws.toolkits.jetbrains.services.codewhisperer.CodeWhispererTestUtil.pythonTestLeftContext

class CodeWhispererTypeaheadTest : CodeWhispererTestBase() {

    @Test
    fun `test typing typeahead should update typeahead state when no right context`() {
        testTypingTypeaheadMatchingRecommendationShouldMatchRightContext("")
    }

    @Test
    fun `test typing typeahead should update typeahead state when there is right context with extra opening bracket`() {
        val testRightContext = "() {\n"
        testTypingTypeaheadMatchingRecommendationShouldMatchRightContext(testRightContext)
    }

    @Test
    fun `test typing typeahead should update typeahead state when there is right context with extra closing bracket`() {
        val testRightContext = "() }"
        testTypingTypeaheadMatchingRecommendationShouldMatchRightContext(testRightContext)
    }

    @Test
    fun `test typing typeahead should update typeahead state when there is right context with trailing spaces`() {
        val testRightContext = "() {\n    }      "
        testTypingTypeaheadMatchingRecommendationShouldMatchRightContext(testRightContext)
    }

    private fun testTypingTypeaheadMatchingRecommendationShouldMatchRightContext(rightContext: String) {
        projectRule.fixture.configureByText(pythonFileName, pythonTestLeftContext + rightContext)
        runInEdtAndWait {
            projectRule.fixture.editor.caretModel.moveToOffset(pythonTestLeftContext.length)
        }
        withCodeWhispererServiceInvokedAndWait { session ->
            var preview = codewhispererService.getAllSuggestionsPreviewInfo()[0]
            val recommendation = preview.detail.reformatted.content()
            val editor = projectRule.fixture.editor
            val startOffset = editor.caretModel.offset
            recommendation.forEachIndexed { index, char ->
                if (index < editor.caretModel.offset - startOffset) return@forEachIndexed
                projectRule.fixture.type(char)
                val caretOffset = editor.caretModel.offset
                val typeahead = editor.document.charsSequence.subSequence(startOffset, caretOffset).toString()
                preview = codewhispererService.getAllSuggestionsPreviewInfo()[0]
                assertThat(preview.typeahead).isEqualTo(typeahead)
            }
        }
    }
}
