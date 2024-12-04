// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.codewhisperer.codescan.actions

import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ex.CheckboxAction
import software.aws.toolkits.jetbrains.services.codewhisperer.codescan.CodeWhispererCodeScanManager
import software.aws.toolkits.jetbrains.services.codewhisperer.codescan.utils.IssueSeverity

class CodeWhispererCodeScanFilterGroup : ActionGroup() {
    override fun getChildren(e: AnActionEvent?): Array<out AnAction> =
        IssueSeverity.entries.map { FilterBySeverityAction(e, it.displayName) }.toTypedArray()

    private class FilterBySeverityAction(event: AnActionEvent?, severity: String) : CheckboxAction() {
        override fun getActionUpdateThread() = ActionUpdateThread.BGT
        private val severity = severity

        override fun isSelected(event: AnActionEvent): Boolean {
            val project = event.project ?: return false
            return CodeWhispererCodeScanManager.getInstance(project).isSeveritySelected(severity)
        }

        override fun setSelected(event: AnActionEvent, state: Boolean) {
            val project = event.project
            if (project != null) {
                CodeWhispererCodeScanManager.getInstance(project).setSeveritySelected(severity, state)
            }
        }

        override fun update(e: AnActionEvent) {
            super.update(e)
            e.presentation.text = severity
        }
    }
}
