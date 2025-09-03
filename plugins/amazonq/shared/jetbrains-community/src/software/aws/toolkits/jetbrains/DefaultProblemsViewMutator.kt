// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains

import com.intellij.analysis.problemsView.toolWindow.ProblemsView
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow

class DefaultProblemsViewMutator : ProblemsViewMutator {
    override fun mutateProblemsView(project: Project, runnable: (ToolWindow) -> Unit) {
        ProblemsView.getToolWindow(project)?.let { runnable(it) }
    }
}
