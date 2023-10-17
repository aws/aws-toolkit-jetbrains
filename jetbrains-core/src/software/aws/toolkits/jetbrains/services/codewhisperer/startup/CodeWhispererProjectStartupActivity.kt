// Copyright 2022 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.codewhisperer.startup

import com.intellij.codeInsight.lookup.LookupManagerListener
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity
import software.aws.toolkits.jetbrains.services.codewhisperer.explorer.CodeWhispererExplorerActionManager
import software.aws.toolkits.jetbrains.services.codewhisperer.explorer.isCodeWhispererEnabled
import software.aws.toolkits.jetbrains.services.codewhisperer.explorer.isCodeWhispererExpired
import software.aws.toolkits.jetbrains.services.codewhisperer.importadder.CodeWhispererImportAdderListener
import software.aws.toolkits.jetbrains.services.codewhisperer.learn.LearnCodeWhispererEditorProvider
import software.aws.toolkits.jetbrains.services.codewhisperer.popup.CodeWhispererPopupManager.Companion.CODEWHISPERER_USER_ACTION_PERFORMED
import software.aws.toolkits.jetbrains.services.codewhisperer.status.CodeWhispererStatusBarManager
import software.aws.toolkits.jetbrains.services.codewhisperer.util.CodeWhispererUtil.promptReAuth

// TODO: add logics to check if we want to remove recommendation suspension date when user open the IDE
class CodeWhispererProjectStartupActivity : StartupActivity.DumbAware {
    private var runOnce = false

    /**
     * Should be invoked when
     * (1) new users accept CodeWhisperer ToS (have to be triggered manually))
     * (2) existing users open the IDE (automatically triggered)
     */
    override fun runActivity(project: Project) {
        if (!ApplicationManager.getApplication().isUnitTestMode) {
            CodeWhispererStatusBarManager.getInstance(project).updateWidget()
        }
        if (!isCodeWhispererEnabled(project)) return
        if (runOnce) return

        // Reconnect CodeWhisperer on startup
        promptReAuth(project, isPluginStarting = true)

        // install intellsense autotrigger listener, this only need to be executed 1 time
        project.messageBus.connect().subscribe(LookupManagerListener.TOPIC, CodeWhispererIntelliSenseAutoTriggerListener)
        project.messageBus.connect().subscribe(CODEWHISPERER_USER_ACTION_PERFORMED, CodeWhispererImportAdderListener)

        if (!CodeWhispererExplorerActionManager.getInstance().hasShownNewOnboardingPage() && !isCodeWhispererExpired(project)) {
            showOnboardingPage(project)
        }
        runOnce = true
    }

    private fun showOnboardingPage(project: Project) {
        LearnCodeWhispererEditorProvider.openEditor(project)
    }
}
