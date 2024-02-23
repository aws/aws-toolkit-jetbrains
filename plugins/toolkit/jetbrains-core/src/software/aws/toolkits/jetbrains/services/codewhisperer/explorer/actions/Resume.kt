// Copyright 2022 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.codewhisperer.explorer.actions

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import software.aws.toolkits.jetbrains.services.codewhisperer.explorer.CodeWhispererExplorerActionManager
import software.aws.toolkits.resources.message

class Resume : DumbAwareAction(
    { message("codewhisperer.explorer.resume_auto") },
    AllIcons.Actions.Resume
) {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return

        CodeWhispererExplorerActionManager.getInstance().setAutoSuggestion(project, true)
    }
}
