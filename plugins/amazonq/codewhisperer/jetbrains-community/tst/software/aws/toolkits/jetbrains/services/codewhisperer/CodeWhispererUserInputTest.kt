// Copyright 2022 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.codewhisperer

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.stub
import software.aws.toolkits.jetbrains.services.codewhisperer.CodeWhispererTestUtil.pythonResponse
import software.aws.toolkits.jetbrains.services.codewhisperer.model.TriggerTypeInfo

class CodeWhispererUserInputTest : CodeWhispererTestBase() {

    @Test
    fun `test no user input should show all recommendations`() {
        addUserInputAfterInvocation("")

        withCodeWhispererServiceInvokedAndWait { session ->
            val actualRecommendations = codewhispererService.getAllSuggestionsPreviewInfo().map { it.detail.recommendation.content() }
            assertThat(actualRecommendations).isEqualTo(pythonResponse.completions().map { it.content() })
        }
    }

    @Test
    fun `test have user input should show that recommendations prefix-matching user input are valid`() {
        val userInput = "test"
        addUserInputAfterInvocation(userInput)

        val expectedRecommendations = pythonResponse.completions().map { it.content() }

        withCodeWhispererServiceInvokedAndWait { session ->
            val previews = codewhispererService.getAllSuggestionsPreviewInfo()
            val actualRecommendations = previews.map { it.detail.recommendation.content() }
            assertThat(actualRecommendations).isEqualTo(expectedRecommendations)
            previews.map { it.detail }.forEachIndexed { index, context ->
                val expectedDiscarded = !pythonResponse.completions()[index].content().startsWith(userInput)
                val actualDiscarded = context.isDiscarded
                assertThat(actualDiscarded).isEqualTo(expectedDiscarded)
            }
        }
    }

    @Test
    fun `test have user input and typeahead should show that recommendations prefix-matching user input + typeahead are valid`() {
        val userInput = "test"
        addUserInputAfterInvocation(userInput)

        val typeahead = " recommendation"

        withCodeWhispererServiceInvokedAndWait { session ->
            projectRule.fixture.type(typeahead)
            val previews = codewhispererService.getAllSuggestionsPreviewInfo()
            assertThat(previews[session.selectedIndex].typeahead).isEqualTo(typeahead)
            previews.map { it.detail }.forEachIndexed { index, actualContext ->
                val actualDiscarded = actualContext.isDiscarded
                val expectedDiscarded = !pythonResponse.completions()[index].content().startsWith(userInput + typeahead)
                assertThat(actualDiscarded).isEqualTo(expectedDiscarded)
            }
        }
    }

    @Test
    fun `test have blank user input should show that all recommendations are valid`() {
        val blankUserInput = "    "
        addUserInputAfterInvocation(blankUserInput)
        val userInput = blankUserInput.trimStart()

        withCodeWhispererServiceInvokedAndWait { session ->
            val previews = codewhispererService.getAllSuggestionsPreviewInfo()
            assertThat(previews[session.selectedIndex].userInput).isEqualTo(userInput)
            previews.map { it.detail }.forEachIndexed { _, actualContext ->
                assertThat(actualContext.isDiscarded).isEqualTo(false)
            }
        }
    }

    @Test
    fun `test have user input with leading spaces and matching suffix should show recommendations prefix-matching suffix are valid`() {
        val userInputWithLeadingSpaces = "   test"
        addUserInputAfterInvocation(userInputWithLeadingSpaces)
        val userInput = userInputWithLeadingSpaces.trimStart()

        withCodeWhispererServiceInvokedAndWait { session ->
            val previews = codewhispererService.getAllSuggestionsPreviewInfo()
            assertThat(previews[session.selectedIndex].userInput).isEqualTo(userInput)
            previews.map { it.detail }.forEachIndexed { index, actualContext ->
                val actualDiscarded = actualContext.isDiscarded
                val expectedDiscarded = !pythonResponse.completions()[index].content().startsWith(userInput)
                assertThat(actualDiscarded).isEqualTo(expectedDiscarded)
            }
        }
    }

    private fun addUserInputAfterInvocation(userInput: String) {
        val triggerTypeCaptor = argumentCaptor<TriggerTypeInfo>()
        val editorCaptor = argumentCaptor<Editor>()
        val projectCaptor = argumentCaptor<Project>()
        val psiFileCaptor = argumentCaptor<PsiFile>()
        codewhispererService.stub {
            onGeneric {
                getRequestContext(
                    triggerTypeCaptor.capture(),
                    editorCaptor.capture(),
                    projectCaptor.capture(),
                    psiFileCaptor.capture()
                )
            }.doAnswer {
                val requestContext = codewhispererService.getRequestContext(
                    triggerTypeCaptor.firstValue,
                    editorCaptor.firstValue,
                    projectCaptor.firstValue,
                    psiFileCaptor.firstValue,
                )
                projectRule.fixture.type(userInput)
                requestContext
            }.thenCallRealMethod()
        }
    }
}
