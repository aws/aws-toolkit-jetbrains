// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonq.startup

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.wm.ToolWindowManager
import software.aws.toolkits.jetbrains.core.credentials.AwsBearerTokenConnection
import software.aws.toolkits.jetbrains.core.credentials.ToolkitConnectionManager
import software.aws.toolkits.jetbrains.core.credentials.pinning.QConnection
import software.aws.toolkits.jetbrains.core.gettingstarted.emitUserState
import software.aws.toolkits.jetbrains.services.amazonq.CodeWhispererFeatureConfigService
import software.aws.toolkits.jetbrains.services.amazonq.lsp.AmazonQLspService
import software.aws.toolkits.jetbrains.services.amazonq.profile.QRegionProfileManager
import software.aws.toolkits.jetbrains.services.amazonq.toolwindow.AmazonQToolWindow
import software.aws.toolkits.jetbrains.services.amazonq.toolwindow.AmazonQToolWindowFactory
import software.aws.toolkits.jetbrains.services.codewhisperer.explorer.CodeWhispererExplorerActionManager
import software.aws.toolkits.jetbrains.services.cwc.inline.InlineChatController
import software.aws.toolkits.jetbrains.settings.CodeWhispererSettings
import java.util.concurrent.atomic.AtomicBoolean

class AmazonQStartupActivity : ProjectActivity {
    private val runOnce = AtomicBoolean(false)

    override suspend fun execute(project: Project) {
        if (ApplicationManager.getApplication().isUnitTestMode) return

        ToolkitConnectionManager.getInstance(project).activeConnectionForFeature(QConnection.getInstance())?.let {
            if (it is AwsBearerTokenConnection && CodeWhispererFeatureConfigService.getInstance().getChatWSContext()) {
                CodeWhispererSettings.getInstance().toggleProjectContextEnabled(value = true, passive = true)
            }
        }

        // initialize html contents in BGT so users don't have to wait when they open the tool window
        AmazonQToolWindow.getInstance(project)
        InlineChatController.getInstance(project)

        if (CodeWhispererExplorerActionManager.getInstance().getIsFirstRestartAfterQInstall()) {
            runInEdt {
                val toolWindow = ToolWindowManager.getInstance(project).getToolWindow(AmazonQToolWindowFactory.WINDOW_ID) ?: return@runInEdt
                toolWindow.show()
                CodeWhispererExplorerActionManager.getInstance().setIsFirstRestartAfterQInstall(false)
            }
        }

        QRegionProfileManager.getInstance().validateProfile(project)

        AmazonQLspService.getInstance(project)
        if (runOnce.get()) return
        emitUserState(project)
        runOnce.set(true)
    }
}
