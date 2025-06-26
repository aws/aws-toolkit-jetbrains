// Copyright 2022 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.codewhisperer

import com.intellij.openapi.util.TextRange
import org.assertj.core.api.Assertions.assertThat
import org.junit.Ignore
import org.junit.Test
import software.aws.toolkits.jetbrains.services.codewhisperer.CodeWhispererTestUtil.pythonFileName
import software.aws.toolkits.jetbrains.services.codewhisperer.CodeWhispererTestUtil.pythonResponse
import software.aws.toolkits.jetbrains.services.codewhisperer.service.CodeWhispererAutomatedTriggerType
import software.aws.toolkits.telemetry.CodewhispererLanguage
import software.aws.toolkits.telemetry.CodewhispererTriggerType

@Ignore("This test suite needs a rewrite for JB inline completion API")
class CodeWhispererStateTest : CodeWhispererTestBase() {

    @Test
    fun `test CodeWhisperer invocation sets request metadata correctly`() {
        withCodeWhispererServiceInvokedAndWait { states ->
            val actualRequestContext = states.requestContext
            val editor = projectRule.fixture.editor
            val (actualProject, actualEditor, actualTriggerTypeInfo, actualCaretPosition, actualFileContextInfo) = actualRequestContext
            val (actualCaretContext, actualFilename, actualProgrammingLanguage) = actualFileContextInfo

            assertThat(actualProject).isEqualTo(projectRule.project)
            assertThat(actualEditor).isEqualTo(editor)
            assertThat(actualTriggerTypeInfo.triggerType).isEqualTo(CodewhispererTriggerType.OnDemand)
            assertThat(actualTriggerTypeInfo.automatedTriggerType is CodeWhispererAutomatedTriggerType.Unknown).isTrue()
            assertThat(actualFilename).isEqualTo(pythonFileName)
            assertThat(actualProgrammingLanguage.languageId).isEqualTo(CodewhispererLanguage.Python.toString())

            val expectedCurrOffset = editor.caretModel.offset
            val document = editor.document
            val expectedCaretLeftFileContext = document.getText(TextRange(0, expectedCurrOffset))
            val expectedCaretRightFileContext = document.getText(TextRange(expectedCurrOffset, document.textLength))
            val (actualCaretLeftFileContext, actualCaretRightFileContext) = actualCaretContext
            assertThat(actualCaretLeftFileContext).isEqualTo(expectedCaretLeftFileContext)
            assertThat(actualCaretRightFileContext).isEqualTo(expectedCaretRightFileContext)

            val (actualOffset, actualLine) = actualCaretPosition
            assertThat(actualOffset).isEqualTo(expectedCurrOffset)
            assertThat(actualLine).isEqualTo(document.getLineNumber(expectedCurrOffset))
        }
    }

    @Test
    fun `test CodeWhisperer invocation sets recommendation metadata correctly`() {
        withCodeWhispererServiceInvokedAndWait { states ->
            val actualRecommendationContext = states.recommendationContext
            val (actualDetailContexts, actualUserInput) = actualRecommendationContext

            assertThat(actualUserInput).isEqualTo("")
            val expectedCount = pythonResponse.items.size
            assertThat(actualDetailContexts.size).isEqualTo(expectedCount)
            actualDetailContexts.forEachIndexed { i, actualDetailContext ->
                val (actualItemId, actualCompletion, actualIsDiscarded) = actualDetailContext
                assertThat(actualCompletion.insertText).isEqualTo(pythonResponse.items[i].insertText)
                assertThat(actualItemId).isEqualTo(pythonResponse.items[i].itemId)
                assertThat(actualIsDiscarded).isEqualTo(false)
            }
        }
    }

    @Test
    fun `test CodeWhisperer invocation sets initial typeahead and selected index correctly`() {
        withCodeWhispererServiceInvokedAndWait {
            val sessionContext = popupManagerSpy.sessionContext
            val actualSelectedIndex = sessionContext.selectedIndex
            val actualTypeahead = sessionContext.typeahead
            val actualTypeaheadOriginal = sessionContext.typeaheadOriginal

            assertThat(actualSelectedIndex).isEqualTo(0)
            assertThat(actualTypeahead).isEqualTo("")
            assertThat(actualTypeaheadOriginal).isEqualTo("")
        }
    }
}
