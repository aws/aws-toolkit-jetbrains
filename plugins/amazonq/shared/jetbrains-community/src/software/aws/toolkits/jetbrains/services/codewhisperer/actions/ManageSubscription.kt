// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.codewhisperer.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.progress.currentThreadCoroutineScope
import com.intellij.openapi.project.DumbAware
import kotlinx.coroutines.future.await
import kotlinx.coroutines.launch
import org.eclipse.lsp4j.ExecuteCommandParams
import software.aws.toolkits.core.utils.error
import software.aws.toolkits.core.utils.getLogger
import software.aws.toolkits.jetbrains.core.credentials.AwsBearerTokenConnection
import software.aws.toolkits.jetbrains.core.credentials.ToolkitConnectionManager
import software.aws.toolkits.jetbrains.core.credentials.pinning.CodeWhispererConnection
import software.aws.toolkits.jetbrains.core.credentials.pinning.QConnection
import software.aws.toolkits.jetbrains.core.credentials.sono.isSono
import software.aws.toolkits.jetbrains.services.amazonq.lsp.AmazonQLspService

class ManageSubscription : AnAction(), DumbAware {
    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val project = e.project
        // disable if user is IdC

        if (project == null) {
            e.presentation.isEnabledAndVisible = false
        } else {
            e.presentation.isEnabledAndVisible = (ToolkitConnectionManager.getInstance(project).activeConnectionForFeature(QConnection.getInstance()) as? AwsBearerTokenConnection).isSono()
        }
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        currentThreadCoroutineScope().launch {
            AmazonQLspService.getInstance(project).execute { lsp ->
                lsp.workspaceService.executeCommand(
                    ExecuteCommandParams().apply {
                        this.command = "aws/chat/manageSubscription"
                    }
                )
            }.handleAsync { _, ex ->
                if (ex != null) {
                    LOG.error(ex) { "Failed aws/chat/manageSubscription" }
                }
            }.await()
        }
    }

    companion object {
        private val LOG = getLogger<ManageSubscription>()
    }
}
