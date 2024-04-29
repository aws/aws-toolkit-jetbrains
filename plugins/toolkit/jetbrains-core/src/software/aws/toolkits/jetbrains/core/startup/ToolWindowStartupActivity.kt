// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.core.startup

import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import software.aws.toolkits.jetbrains.core.explorer.webview.ToolkitWebviewPanel
import software.aws.toolkits.jetbrains.core.gettingstarted.emitUserState

class ToolWindowStartupActivity : ProjectActivity {
    private var runOnce = false

    override suspend fun execute(project: Project) {
        // initialize html contents in BGT so users don't have to wait when they open the tool window
        ToolkitWebviewPanel.getInstance(project)

        if (runOnce) return
        emitUserState(project)
        runOnce = true
    }
}
