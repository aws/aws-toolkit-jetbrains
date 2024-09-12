// Copyright 2023 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.codemodernizer.actions

import com.intellij.icons.AllIcons.Vcs.History
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAware
import software.aws.toolkits.jetbrains.services.codemodernizer.CodeModernizerManager
import software.aws.toolkits.resources.message

class CodeModernizerShowJobStatusAction :
    AnAction(
        message("codemodernizer.explorer.show_job_status"),
        message("codemodernizer.explorer.show_job_status_description"),
        History
    ),
    DumbAware {
    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun update(event: AnActionEvent) {
        event.presentation.icon = History
    }

    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        val codeModernizerManager = CodeModernizerManager.getInstance(project)
        codeModernizerManager.showPreviousJobHistoryUI()
    }
}
