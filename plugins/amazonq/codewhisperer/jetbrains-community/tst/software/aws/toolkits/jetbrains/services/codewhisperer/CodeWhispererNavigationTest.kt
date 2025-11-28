// Copyright 2022 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.codewhisperer

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnActionEvent
import org.assertj.core.api.Assertions.assertThat
import org.junit.Ignore
import org.junit.Test
import software.aws.toolkits.jetbrains.services.codewhisperer.CodeWhispererTestUtil.pythonResponse
import software.aws.toolkits.jetbrains.services.codewhisperer.util.CodeWhispererConstants.QInlineActionId.qInlineNavigateNextActionId
import software.aws.toolkits.jetbrains.services.codewhisperer.util.CodeWhispererConstants.QInlineActionId.qInlineNavigatePrevActionId
import javax.swing.JButton

@Ignore("This test suite needs a rewrite for JB inline completion API")
class CodeWhispererNavigationTest : CodeWhispererTestBase() {

    @Test
    fun `test navigating to previous recommendation should decrement selected index and update label texts by clicking button`() {
        testNavigation(true)
    }

    @Test
    fun `test navigating to next recommendation should increment selected index and update label texts by clicking button`() {
        testNavigation(false)
    }

    @Test
    fun `test navigating to previous recommendation should decrement selected index and update label texts key shortcuts`() {
        testNavigation(true, useKeyboard = true)
    }

    @Test
    fun `test navigating to next recommendation should increment selected index and update label texts by key shortcuts`() {
        testNavigation(false, useKeyboard = true)
    }

    private fun testNavigation(isReverse: Boolean, useKeyboard: Boolean = false) {
        withCodeWhispererServiceInvokedAndWait {
            val indexChange = if (isReverse) -1 else 1

            assertThat(popupManagerSpy.sessionContext.selectedIndex).isEqualTo(0)

            val expectedCount = pythonResponse.items.size
            var expectedSelectedIndex: Int
            val navigationButton: JButton
            val oppositeButton: JButton
            if (isReverse) {
                navigationButton = popupManagerSpy.popupComponents.prevButton
                oppositeButton = popupManagerSpy.popupComponents.nextButton
                expectedSelectedIndex = expectedCount - 1
            } else {
                navigationButton = popupManagerSpy.popupComponents.nextButton
                oppositeButton = popupManagerSpy.popupComponents.prevButton
                expectedSelectedIndex = 0
            }
            if (isReverse) {
                repeat(expectedCount - 1) {
                    navigateHelper(false, useKeyboard)
                }
            }

            assertThat(popupManagerSpy.sessionContext.selectedIndex).isEqualTo(expectedSelectedIndex)
            assertThat(oppositeButton.isEnabled).isEqualTo(false)

            repeat(expectedCount - 1) {
                assertThat(navigationButton.isEnabled).isEqualTo(true)
                navigateHelper(isReverse, useKeyboard)
                assertThat(oppositeButton.isEnabled).isEqualTo(true)
                expectedSelectedIndex = (expectedSelectedIndex + indexChange) % expectedCount
                assertThat(popupManagerSpy.sessionContext.selectedIndex).isEqualTo(expectedSelectedIndex)
                checkRecommendationInfoLabelText(expectedSelectedIndex + 1, expectedCount)
            }
            assertThat(navigationButton.isEnabled).isEqualTo(false)
        }
    }

    private fun navigateHelper(isReverse: Boolean, useKeyboard: Boolean) {
        if (useKeyboard) {
            if (isReverse) {
                ActionManager.getInstance().getAction(qInlineNavigatePrevActionId)
                    .actionPerformed(
                        AnActionEvent.createFromDataContext("test", null) { projectRule.project }
                    )
            } else {
                ActionManager.getInstance().getAction(qInlineNavigateNextActionId)
                    .actionPerformed(
                        AnActionEvent.createFromDataContext("test", null) { projectRule.project }
                    )
            }
        } else {
            if (isReverse) {
                popupManagerSpy.popupComponents.prevButton.doClick()
            } else {
                popupManagerSpy.popupComponents.nextButton.doClick()
            }
        }
    }
}
