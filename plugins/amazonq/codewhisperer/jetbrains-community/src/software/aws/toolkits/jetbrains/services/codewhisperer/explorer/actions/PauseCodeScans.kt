// Copyright 2022 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.codewhisperer.explorer.actions

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import software.aws.toolkits.jetbrains.services.codewhisperer.CodeWhispererExplorerActionManager
import software.aws.toolkits.jetbrains.services.codewhisperer.util.CodeWhispererConstants
import software.aws.toolkits.resources.message
import software.aws.toolkits.telemetry.AwsTelemetry

class PauseCodeScans : DumbAwareAction(
    { message("codewhisperer.explorer.pause_auto_scans") },
    AllIcons.Actions.Pause
) {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return

        CodeWhispererExplorerActionManager.getInstance().setAutoCodeScan(false)
        AwsTelemetry.modifySetting(
            project,
            settingId = CodeWhispererConstants.AutoCodeScan.SETTING_ID,
            settingState = CodeWhispererConstants.AutoCodeScan.DEACTIVATED
        )
    }
}
