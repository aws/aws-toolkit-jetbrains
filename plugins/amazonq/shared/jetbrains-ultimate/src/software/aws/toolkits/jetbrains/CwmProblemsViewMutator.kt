// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains

import com.intellij.analysis.problemsView.toolWindow.ProblemsView
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.jetbrains.rdserver.toolWindow.BackendToolWindowHost

class CwmProblemsViewMutator : ProblemsViewMutator {
    override fun mutateProblemsView(project: Project, runnable: (ToolWindow) -> Unit) {
        BackendToolWindowHost.getAllInstances(project).forEach { host ->
            host.getToolWindow(ProblemsView.ID)?.let {
                runnable(it)
            }
        }
    }
}
