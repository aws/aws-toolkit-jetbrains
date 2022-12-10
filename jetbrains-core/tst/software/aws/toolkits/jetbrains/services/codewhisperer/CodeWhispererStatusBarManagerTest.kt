// Copyright 2022 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.codewhisperer

import com.intellij.openapi.application.ApplicationManager
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.RuleChain
import com.intellij.testFramework.replaceService
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doReturnConsecutively
import org.mockito.kotlin.mock
import org.mockito.kotlin.stub
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import software.aws.toolkits.jetbrains.core.credentials.ManagedBearerSsoConnection
import software.aws.toolkits.jetbrains.core.credentials.ToolkitConnectionManagerListener
import software.aws.toolkits.jetbrains.services.codewhisperer.status.CodeWhispererStatusBarManager

class CodeWhispererStatusBarManagerTest {
    val applicationRule = ApplicationRule()

    val projectRule = ProjectRule()

    @JvmField
    @Rule
    val ruleChain = RuleChain(applicationRule, projectRule)

    lateinit var sut: CodeWhispererStatusBarManager

    @Before
    fun setup() {
        sut = CodeWhispererStatusBarManager(projectRule.project)
    }

    @Test
    fun `test1`() {
        println(sut)
        ApplicationManager.getApplication().messageBus.syncPublisher(ToolkitConnectionManagerListener.TOPIC).activeConnectionChanged(mock<ManagedBearerSsoConnection>())

    }
}
