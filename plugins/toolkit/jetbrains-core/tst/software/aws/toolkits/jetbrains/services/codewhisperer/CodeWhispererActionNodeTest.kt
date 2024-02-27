// Copyright 2022 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.codewhisperer

import com.intellij.icons.AllIcons
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.TestActionEvent
import com.intellij.testFramework.replaceService
import kotlinx.coroutines.Job
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.spy
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import software.aws.toolkits.jetbrains.core.credentials.ToolkitConnectionManager
import software.aws.toolkits.jetbrains.services.codemodernizer.CodeModernizerManager
import software.aws.toolkits.jetbrains.services.codemodernizer.explorer.nodes.CodeModernizerRunModernizeNode
import software.aws.toolkits.jetbrains.services.codemodernizer.model.ValidationResult
import software.aws.toolkits.jetbrains.services.codewhisperer.codescan.CodeWhispererCodeScanManager
import software.aws.toolkits.jetbrains.services.codewhisperer.explorer.CodeWhispererExplorerActionManager
import software.aws.toolkits.jetbrains.services.codewhisperer.explorer.actions.OpenCodeReference
import software.aws.toolkits.jetbrains.services.codewhisperer.explorer.nodes.CodeWhispererActionNode
import software.aws.toolkits.jetbrains.services.codewhisperer.explorer.nodes.OpenCodeReferenceNode
import software.aws.toolkits.jetbrains.services.codewhisperer.explorer.nodes.RunCodeScanNode
import software.aws.toolkits.jetbrains.services.codewhisperer.toolwindow.CodeWhispererCodeReferenceManager

class CodeWhispererActionNodeTest {
    @JvmField
    @Rule
    val applicationRule = ApplicationRule()

    @JvmField
    @Rule
    val projectRule = ProjectRule()

    @JvmField
    @Rule
    val disposableRule = DisposableRule()

    private lateinit var project: Project
    private lateinit var sut: CodeWhispererActionNode
    private lateinit var explorerManager: CodeWhispererExplorerActionManager
    private lateinit var connectionManager: ToolkitConnectionManager
    private lateinit var codeScanManager: CodeWhispererCodeScanManager
    private lateinit var codeModernizerManager: CodeModernizerManager

    @Before
    fun setup() {
        project = projectRule.project

        explorerManager = spy()
        ApplicationManager.getApplication().replaceService(CodeWhispererExplorerActionManager::class.java, explorerManager, disposableRule.disposable)

        connectionManager = mock()
        project.replaceService(ToolkitConnectionManager::class.java, connectionManager, disposableRule.disposable)

        codeScanManager = mock()
        project.replaceService(CodeWhispererCodeScanManager::class.java, codeScanManager, disposableRule.disposable)

        codeModernizerManager = mock()
        project.replaceService(CodeModernizerManager::class.java, codeModernizerManager, disposableRule.disposable)
    }

    @Test
    fun `openCodeReferenceNode`() {
        val sut = OpenCodeReferenceNode(project)
        val referenceManager: CodeWhispererCodeReferenceManager = mock()
        project.replaceService(CodeWhispererCodeReferenceManager::class.java, referenceManager, disposableRule.disposable)

        sut.onDoubleClick(mock())

        verify(referenceManager).showCodeReferencePanel()
    }

    @Test
    fun `openCodeReference`() {
        val sut = OpenCodeReference()
        val referenceManager: CodeWhispererCodeReferenceManager = mock()
        project.replaceService(CodeWhispererCodeReferenceManager::class.java, referenceManager, disposableRule.disposable)

        sut.actionPerformed(TestActionEvent { projectRule.project })

        verify(referenceManager).showCodeReferencePanel()
    }

    @Test
    fun `runCodeScanNode`() {
        whenever(codeScanManager.getActionButtonIconForExplorerNode()).thenReturn(mock())
        whenever(codeScanManager.getActionButtonText()).thenReturn("")

        sut = RunCodeScanNode(project)

        sut.onDoubleClick(mock())

        verify(codeScanManager).runCodeScan()
    }

    @Test
    fun `runCodeModernizer`() {
        whenever(codeModernizerManager.validate(any())).thenReturn(ValidationResult(true))
        whenever(codeModernizerManager.getRunActionButtonIcon()).thenReturn(AllIcons.Actions.Execute)
        whenever(codeModernizerManager.runModernize(any())).thenReturn(Job())
        sut = CodeModernizerRunModernizeNode(project)

        sut.onDoubleClick(mock())

        verify(codeModernizerManager).validateAndStart()
        verify(codeModernizerManager).getRunActionButtonIcon()
    }
}
