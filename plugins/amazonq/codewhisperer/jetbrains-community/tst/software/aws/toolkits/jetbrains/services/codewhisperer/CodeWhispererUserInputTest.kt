// Copyright 2022 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.codewhisperer

import org.assertj.core.api.Assertions.assertThat
import org.junit.Ignore
import org.junit.Test
import software.aws.toolkits.jetbrains.services.codewhisperer.CodeWhispererTestUtil.pythonResponse

@Ignore("This test suite needs a rewrite for JB inline completion API")
class CodeWhispererUserInputTest : CodeWhispererTestBase() {

    @Test
    fun `test no user input should show all recommendations`() {
        addUserInputAfterInvocation("")

        withCodeWhispererServiceInvokedAndWait { states ->
            val actualRecommendations = states.recommendationContext.details.map {
                it.completion.insertText
            }
            assertThat(actualRecommendations).isEqualTo(pythonResponse.items.map { it.insertText })
        }
    }

    @Test
    fun `test have user input should show that recommendations prefix-matching user input are valid`() {
        val userInput = "test"
        addUserInputAfterInvocation(userInput)

        val expectedRecommendations = pythonResponse.items.map { it.insertText }

        withCodeWhispererServiceInvokedAndWait { states ->
            val actualRecommendations = states.recommendationContext.details.map { it.completion.insertText }
            assertThat(actualRecommendations).isEqualTo(expectedRecommendations)
            states.recommendationContext.details.forEachIndexed { index, context ->
                val expectedDiscarded = !pythonResponse.items[index].insertText.startsWith(userInput)
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

        withCodeWhispererServiceInvokedAndWait { states ->
            projectRule.fixture.type(typeahead)
            assertThat(popupManagerSpy.sessionContext.typeahead).isEqualTo(typeahead)
            states.recommendationContext.details.forEachIndexed { index, actualContext ->
                val actualDiscarded = actualContext.isDiscarded
                val expectedDiscarded = !pythonResponse.items[index].insertText.startsWith(userInput + typeahead)
                assertThat(actualDiscarded).isEqualTo(expectedDiscarded)
            }
        }
    }
}
