// Copyright 2022 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.codewhisperer.actions

import com.intellij.icons.AllIcons
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAware
import software.aws.toolkits.jetbrains.services.amazonq.QConstants
import software.aws.toolkits.jetbrains.services.codewhisperer.explorer.isCodeWhispererEnabled
import software.aws.toolkits.resources.message
import java.net.URI

class CodeWhispererWhatIsAction :
    AnAction(
        message("codewhisperer.explorer.what_is"),
        null,
        AllIcons.Actions.Help
    ),
    DumbAware {
    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        e.project?.let {
            e.presentation.isEnabledAndVisible = isCodeWhispererEnabled(it)
        }
    }

    override fun actionPerformed(e: AnActionEvent) {
        BrowserUtil.browse(URI(QConstants.Q_MARKETPLACE_URI))
    }
}
