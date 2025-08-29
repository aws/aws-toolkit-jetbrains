// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow

interface ProblemsViewMutator {
    fun mutateProblemsView(project: Project, runnable: (ToolWindow) -> Unit)

    companion object {
        val EP = ExtensionPointName<ProblemsViewMutator>("software.aws.toolkits.jetbrains.problemsViewMutator")
    }
}
