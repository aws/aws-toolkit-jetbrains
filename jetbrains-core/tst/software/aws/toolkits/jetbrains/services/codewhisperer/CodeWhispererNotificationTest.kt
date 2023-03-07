// Copyright 2023 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.codewhisperer

import com.intellij.internal.statistic.eventLog.FeatureUsageUiEvents
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.testFramework.TestActionEvent
import com.intellij.testFramework.replaceService
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.spy

class CodeWhispererNotificationTest : CodeWhispererBasicTestBase() {
    private lateinit var event: AnActionEvent
    private lateinit var featureUsageUiEventsSpy: FeatureUsageUiEvents

    @Before
    fun setUp() {
        featureUsageUiEventsSpy = spy(ApplicationManager.getApplication().getService(FeatureUsageUiEvents::class.java))
        ApplicationManager.getApplication().replaceService(FeatureUsageUiEvents::class.java, featureUsageUiEventsSpy, disposableRule.disposable)
        event = TestActionEvent { key ->
            when (key) {
                CommonDataKeys.PROJECT.name -> projectRule.project
                else -> null
            }
        }
    }

    @Test
    fun `ConnectWithAwsToContinueActionWarn actionPerformed should open CodeWhispererLoginDialog`() {
//        val action = ConnectWithAwsToContinueActionWarn()
//        action.actionPerformed(event)
//        runInEdtAndWait {
//            val dialogIdCaptor = argumentCaptor<String>()
//            val classCaptor = argumentCaptor<Class<*>>()
//            verify(featureUsageUiEventsSpy).logShowDialog(dialogIdCaptor.capture(), classCaptor.capture())
//            assertThat(dialogIdCaptor.lastValue).isEqualTo("")
//            assertThat(classCaptor.lastValue).isEqualTo(CodeWhispererLoginDialog::class.java)
//        }
    }

    @Test
    fun `ConnectWithAwsToContinueActionError actionPerformed should open CodeWhispererLoginDialog`() {
//        val action = ConnectWithAwsToContinueActionError()
//        action.actionPerformed(actionEvent)
//        verify(actionEvent.project, times(1))?.let {
//            CodeWhispererLoginDialog(it).showAndGet()
//        }
    }
}
