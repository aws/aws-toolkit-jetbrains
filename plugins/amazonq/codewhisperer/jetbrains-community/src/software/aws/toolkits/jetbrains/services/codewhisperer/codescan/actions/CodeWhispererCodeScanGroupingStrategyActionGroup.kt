// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.codewhisperer.codescan.actions

import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ex.CheckboxAction
import software.aws.toolkits.jetbrains.services.codewhisperer.codescan.CodeWhispererCodeScanManager
import software.aws.toolkits.jetbrains.services.codewhisperer.codescan.utils.IssueGroupingStrategy

class CodeWhispererCodeScanGroupingStrategyActionGroup : ActionGroup() {
    override fun getChildren(e: AnActionEvent?): Array<out AnAction> = IssueGroupingStrategy.entries.map { GroupByAction(it) }.toTypedArray()

    private class GroupByAction(private val groupingStrategy: IssueGroupingStrategy) : CheckboxAction() {
        override fun getActionUpdateThread() = ActionUpdateThread.BGT

        override fun isSelected(event: AnActionEvent): Boolean {
            val project = event.project ?: return false
            return CodeWhispererCodeScanManager.getInstance(project).getGroupingStrategySelected() == groupingStrategy
        }

        override fun setSelected(event: AnActionEvent, state: Boolean) {
            val project = event.project ?: return
            if (state) {
                CodeWhispererCodeScanManager.getInstance(project).setGroupingStrategySelected(groupingStrategy)
            }
        }

        override fun update(e: AnActionEvent) {
            super.update(e)
            e.presentation.text = groupingStrategy.displayName
        }
    }
}
