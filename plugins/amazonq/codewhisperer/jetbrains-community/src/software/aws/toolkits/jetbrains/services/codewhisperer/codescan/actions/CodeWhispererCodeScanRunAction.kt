// Copyright 2022 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.codewhisperer.codescan.actions

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.project.DumbAwareAction
import software.aws.toolkits.jetbrains.services.codewhisperer.codescan.CodeWhispererCodeScanManager
import software.aws.toolkits.jetbrains.services.codewhisperer.explorer.isCodeWhispererEnabled
import software.aws.toolkits.jetbrains.services.codewhisperer.util.CodeWhispererColorUtil.INACTIVE_TEXT_COLOR
import software.aws.toolkits.jetbrains.services.codewhisperer.util.CodeWhispererConstants.runScanKey
import software.aws.toolkits.resources.message

class CodeWhispererCodeScanRunAction : DumbAwareAction(
    message("codewhisperer.codescan.run_scan", INACTIVE_TEXT_COLOR),
    null,
    AllIcons.Actions.Execute
) {
    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun update(event: AnActionEvent) {
        val project = event.project ?: return
        event.presentation.isEnabledAndVisible = isCodeWhispererEnabled(project)
        val scanManager = CodeWhispererCodeScanManager.getInstance(project)
        event.presentation.icon = scanManager.getRunActionButtonIcon()
    }

    override fun actionPerformed(event: AnActionEvent) {
        val dataContext = SimpleDataContext.builder()
            .setParent(event.dataContext)
            .add(runScanKey, true)
            .build()
        val actionEvent = AnActionEvent.createFromDataContext("", null, dataContext)
        ActionManager.getInstance().getAction("q.openchat").actionPerformed(actionEvent)
    }
}
