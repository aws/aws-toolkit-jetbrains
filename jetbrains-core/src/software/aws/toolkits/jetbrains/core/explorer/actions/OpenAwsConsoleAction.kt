// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.core.explorer.actions

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.DumbAware
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import software.aws.toolkits.core.credentials.AwsConsoleUrlFactory
import software.aws.toolkits.jetbrains.core.awsClient
import software.aws.toolkits.jetbrains.core.credentials.activeCredentialProvider
import software.aws.toolkits.jetbrains.core.credentials.activeRegion
import software.aws.toolkits.jetbrains.core.explorer.JcefClientService
import software.aws.toolkits.jetbrains.core.explorer.browservfs.BrowserVirtualFile
import software.aws.toolkits.jetbrains.core.explorer.nodes.AwsExplorerNode
import software.aws.toolkits.jetbrains.services.s3.editor.S3VirtualBucket
import software.aws.toolkits.jetbrains.utils.ApplicationThreadPoolScope
import software.aws.toolkits.jetbrains.utils.getCoroutineUiContext
import java.awt.Window

class OpenAwsConsoleAction: SingleExplorerNodeAction<AwsExplorerNode<*>>("Open AWS Console", icon = AllIcons.General.Web), DumbAware {
    private val edtContext = getCoroutineUiContext()

    override fun actionPerformed(selected: AwsExplorerNode<*>, e: AnActionEvent) {
        val creds = selected.nodeProject.activeCredentialProvider()
        val region = selected.nodeProject.activeRegion()
        val fragment = selected.consoleFragment() ?: return
        ApplicationThreadPoolScope("ok").launch {
            withContext(edtContext) {
                FileEditorManager.getInstance(selected.nodeProject).openTextEditor(
                    OpenFileDescriptor(
                        selected.nodeProject,
                        BrowserVirtualFile(creds, region)
                    ), true
                )
            }
            val browser = JcefClientService.getInstance().getBrowser(creds, region)

            // TODO: can't use [JBCefBrowser.loadURL] directly since it thinks the browser is uninitialized when we pass in our own CefBrowser
            browser.cefBrowser.loadURL(AwsConsoleUrlFactory.consoleUrl(fragment, region))
//            (browser.component.rootPane.parent as Window).toFront()
        }
    }

    override fun update(selected: AwsExplorerNode<*>, e: AnActionEvent) {
        e.presentation.isEnabled = selected.consoleFragment() != null
    }
}
