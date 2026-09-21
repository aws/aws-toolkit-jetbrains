// Copyright 2026 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cdklsp

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.platform.lsp.api.LspServerManager
import software.aws.toolkits.jetbrains.services.cdklsp.server.CdkLspServerSupportProvider
import software.aws.toolkits.jetbrains.settings.CdkLspSettingsChangeListener

/**
 * Restarts the CDK language server when aws.cdk.cliPath / aws.cdk.appDir change,
 * so a new CLI or app dir takes effect without an IDE restart.
 */
internal class CdkLspStartupActivity : ProjectActivity {
    override suspend fun execute(project: Project) {
        ApplicationManager.getApplication().messageBus
            .connect(project)
            .subscribe(
                CdkLspSettingsChangeListener.TOPIC,
                CdkLspSettingsChangeListener {
                    LspServerManager.getInstance(project)
                        .stopAndRestartIfNeeded(CdkLspServerSupportProvider::class.java)
                }
            )
    }
}
