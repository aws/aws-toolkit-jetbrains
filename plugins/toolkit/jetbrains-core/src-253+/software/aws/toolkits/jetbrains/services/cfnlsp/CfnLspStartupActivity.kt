// Copyright 2026 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cfnlsp

import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.platform.lsp.api.LspServerManager
import software.aws.toolkits.jetbrains.services.cfnlsp.server.CfnLspServerDescriptor
import software.aws.toolkits.jetbrains.services.cfnlsp.server.CfnLspServerSupportProvider

internal class CfnLspStartupActivity : ProjectActivity {
    override suspend fun execute(project: Project) {
        CfnCredentialsService.getInstance(project) // eagerly initialize to register settings change listener
        LspServerManager.getInstance(project).ensureServerStarted(
            CfnLspServerSupportProvider::class.java,
            CfnLspServerDescriptor.getInstance(project)
        )
    }
}
