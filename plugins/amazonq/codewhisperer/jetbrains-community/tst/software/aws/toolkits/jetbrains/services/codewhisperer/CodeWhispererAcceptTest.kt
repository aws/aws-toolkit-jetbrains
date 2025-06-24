// Copyright 2022 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.codewhisperer

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.testFramework.runInEdtAndWait
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Ignore
import org.junit.Test
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.textDocument.InlineCompletionItem
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.textDocument.InlineCompletionListWithReferences
import software.aws.toolkits.jetbrains.services.codewhisperer.CodeWhispererTestUtil.javaFileName
import software.aws.toolkits.jetbrains.services.codewhisperer.CodeWhispererTestUtil.javaResponse
import software.aws.toolkits.jetbrains.services.codewhisperer.CodeWhispererTestUtil.javaTestContext
import software.aws.toolkits.jetbrains.services.codewhisperer.CodeWhispererTestUtil.testNextToken
import software.aws.toolkits.jetbrains.services.codewhisperer.util.CodeWhispererConstants.QInlineActionId.qInlineAcceptActionId

@Ignore("This test suite needs a rewrite for JB inline completion API")
class CodeWhispererAcceptTest : CodeWhispererTestBase() {

    @Before
    override fun setUp() {
        super.setUp()

        // Use java code to test curly braces behavior
        projectRule.fixture.configureByText(javaFileName, javaTestContext)
        mockLspInlineCompletionResponse(javaResponse)
        runInEdtAndWait {
            projectRule.fixture.editor.caretModel.moveToOffset(projectRule.fixture.editor.document.textLength - 2)
        }
    }

    @Test
    fun `test accept recommendation with no typeahead no matching brackets on the right`() {
        testAcceptRecommendationWithTypingAndMatchingBracketsOnTheRight("", "")
    }

    @Test
    fun `test accept recommendation with no typeahead no matching brackets on the right using keyboard`() {
        testAcceptRecommendationWithTypingAndMatchingBracketsOnTheRight("", "", useKeyboard = true)
    }

    @Test
    fun `test accept recommendation with no typeahead with matching brackets on the right`() {
        testAcceptRecommendationWithTypingAndMatchingBracketsOnTheRight("", "(")
        testAcceptRecommendationWithTypingAndMatchingBracketsOnTheRight("", "()")
        testAcceptRecommendationWithTypingAndMatchingBracketsOnTheRight("", "() {")
        testAcceptRecommendationWithTypingAndMatchingBracketsOnTheRight("", "{")
        testAcceptRecommendationWithTypingAndMatchingBracketsOnTheRight("", "(){")
        testAcceptRecommendationWithTypingAndMatchingBracketsOnTheRight("", "() {")
        testAcceptRecommendationWithTypingAndMatchingBracketsOnTheRight("", "() {\n    }")
        testAcceptRecommendationWithTypingAndMatchingBracketsOnTheRight("", "() {\n    }     ")
    }

    @Test
    fun `test accept recommendation with typeahead with matching brackets on the right`() {
        val lastIndexOfNewLine = javaResponse.items[0].insertText.lastIndexOf("\n")
        val recommendation = javaResponse.items[0].insertText.substring(0, lastIndexOfNewLine)
        recommendation.indices.forEach {
            val typeahead = recommendation.substring(0, it + 1)
            testAcceptRecommendationWithTypingAndMatchingBracketsOnTheRight(typeahead, "(")
            testAcceptRecommendationWithTypingAndMatchingBracketsOnTheRight(typeahead, "()")
            testAcceptRecommendationWithTypingAndMatchingBracketsOnTheRight(typeahead, "() {")
            testAcceptRecommendationWithTypingAndMatchingBracketsOnTheRight(typeahead, "{")
            testAcceptRecommendationWithTypingAndMatchingBracketsOnTheRight(typeahead, "(){")
            testAcceptRecommendationWithTypingAndMatchingBracketsOnTheRight(typeahead, "() {")
            testAcceptRecommendationWithTypingAndMatchingBracketsOnTheRight(typeahead, "() {\n    }")
            testAcceptRecommendationWithTypingAndMatchingBracketsOnTheRight(typeahead, "() {\n    }     ")
        }
    }

    @Test
    fun `test accept single-line recommendation with no typeahead with partial matching brackets on the right`() {
        mockLspInlineCompletionResponse(
            InlineCompletionListWithReferences(
                listOf(
                    InlineCompletionItem(
                        itemId = "item1",
                        insertText = "(x, y) {",
                        null,
                        null
                    )
                ),
                sessionId = "sessionId",
                partialResultToken = testNextToken
            )
        )

        // any non-matching first-line right context should remain
        testAcceptRecommendationWithTypingAndMatchingBracketsOnTheRight("", "(", "test")
        testAcceptRecommendationWithTypingAndMatchingBracketsOnTheRight("", "()", "test")
        testAcceptRecommendationWithTypingAndMatchingBracketsOnTheRight("", "() {", "test")
        testAcceptRecommendationWithTypingAndMatchingBracketsOnTheRight("", "({", "test")
    }

    @Test
    fun `test accept multi-line recommendation with no typeahead with partial matching brackets on the right`() {
        // any first-line right context should be overwritten
        testAcceptRecommendationWithTypingAndMatchingBracketsOnTheRight("", "(test")
        testAcceptRecommendationWithTypingAndMatchingBracketsOnTheRight("", "()test")
        testAcceptRecommendationWithTypingAndMatchingBracketsOnTheRight("", "() {test")
        testAcceptRecommendationWithTypingAndMatchingBracketsOnTheRight("", "({test")
    }

    private fun testAcceptRecommendationWithTypingAndMatchingBracketsOnTheRight(
        typing: String,
        brackets: String,
        remaining: String = "",
        useKeyboard: Boolean = false,
    ) {
        projectRule.fixture.configureByText(javaFileName, buildContextWithRecommendation(brackets + remaining))
        runInEdtAndWait {
            // move the cursor to the correct trigger point (...void main<trigger>)
            projectRule.fixture.editor.caretModel.moveToOffset(47)
        }
        withCodeWhispererServiceInvokedAndWait { states ->
            val recommendation = states.recommendationContext.details[0].completion.insertText
            val editor = projectRule.fixture.editor
            val expectedContext = buildContextWithRecommendation(recommendation + remaining)
            val startOffset = editor.caretModel.offset
            typing.forEachIndexed { index, char ->
                if (index < editor.caretModel.offset - startOffset) return@forEachIndexed
                projectRule.fixture.type(char)
            }
            acceptHelper(useKeyboard)
            assertThat(editor.document.text).isEqualTo(expectedContext)
        }
    }

    private fun acceptHelper(useKeyboard: Boolean) {
        if (useKeyboard) {
            ActionManager.getInstance().getAction(qInlineAcceptActionId)
                .actionPerformed(
                    AnActionEvent.createFromDataContext("", null) { projectRule.project }
                )
        } else {
            popupManagerSpy.popupComponents.acceptButton.doClick()
        }
    }

    private fun buildContextWithRecommendation(recommendation: String): String {
        val lastIndexOfNewLine = javaTestContext.length - 2
        return javaTestContext.substring(0, lastIndexOfNewLine) + recommendation + javaTestContext.substring(lastIndexOfNewLine)
    }
}
