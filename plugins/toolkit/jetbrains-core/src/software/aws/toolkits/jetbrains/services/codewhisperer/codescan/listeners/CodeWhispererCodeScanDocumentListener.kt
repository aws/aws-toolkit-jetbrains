// Copyright 2022 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.codewhisperer.codescan.listeners

import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import software.aws.toolkits.jetbrains.services.codewhisperer.codescan.CodeWhispererCodeScanIssue
import software.aws.toolkits.jetbrains.services.codewhisperer.codescan.CodeWhispererCodeScanManager
import software.aws.toolkits.jetbrains.services.codewhisperer.explorer.CodeWhispererExplorerActionManager
import software.aws.toolkits.jetbrains.services.codewhisperer.explorer.isUserBuilderId
import software.aws.toolkits.jetbrains.services.codewhisperer.util.CodeWhispererConstants
import javax.swing.tree.TreePath

internal class CodeWhispererCodeScanDocumentListener(val project: Project) : DocumentListener {

    override fun documentChanged(event: DocumentEvent) {
        val file = FileDocumentManager.getInstance().getFile(event.document) ?: return
        val scanManager = CodeWhispererCodeScanManager.getInstance(project)
        val treeModel = scanManager.getScanTree().model

        val editedTextRange = TextRange.create(event.offset, event.offset + event.oldLength)
        val nodes = scanManager.getOverlappingScanNodes(file, editedTextRange)
        nodes.forEach {
            val issue = it.userObject as CodeWhispererCodeScanIssue
            synchronized(it) {
                treeModel.valueForPathChanged(TreePath(it.path), issue.copy(isInvalid = true))
            }
            issue.rangeHighlighter?.dispose()
            issue.rangeHighlighter?.textAttributes = null
        }
        scanManager.updateScanNodes(file)

        if (CodeWhispererExplorerActionManager.getInstance().isAutoEnabledForCodeScan() &&
            !CodeWhispererExplorerActionManager.getInstance().isMonthlyQuotaForCodeScansExceeded() &&
            !isUserBuilderId(project)
        ) {
            scanManager.createDebouncedRunCodeScan(CodeWhispererConstants.CodeAnalysisScope.FILE)
        }
    }
}
