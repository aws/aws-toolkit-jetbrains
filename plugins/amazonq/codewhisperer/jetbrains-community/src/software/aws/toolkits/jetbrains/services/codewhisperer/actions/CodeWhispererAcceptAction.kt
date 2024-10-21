// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.codewhisperer.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.DumbAware
import software.aws.toolkits.jetbrains.services.codewhisperer.popup.CodeWhispererPopupManager
import software.aws.toolkits.jetbrains.services.codewhisperer.service.CodeWhispererInvocationStatusNew
import software.aws.toolkits.jetbrains.services.codewhisperer.service.CodeWhispererServiceNew
import software.aws.toolkits.resources.message

open class CodeWhispererAcceptAction(title: String = message("codewhisperer.inline.accept")) : AnAction(title), DumbAware {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabled = e.project != null && e.getData(CommonDataKeys.EDITOR) != null &&
            CodeWhispererInvocationStatusNew.getInstance().isDisplaySessionActive()
    }

    override fun actionPerformed(e: AnActionEvent) {
        val sessionContext = e.project?.getUserData(CodeWhispererServiceNew.KEY_SESSION_CONTEXT) ?: return
        if (!CodeWhispererInvocationStatusNew.getInstance().isDisplaySessionActive()) return
        ApplicationManager.getApplication().messageBus.syncPublisher(
            CodeWhispererPopupManager.CODEWHISPERER_USER_ACTION_PERFORMED
        ).beforeAccept(sessionContext)
    }
}

// A same accept action but different key shortcut and different promoter logic
class CodeWhispererForceAcceptAction(title: String = message("codewhisperer.inline.force.accept")) : CodeWhispererAcceptAction(title)
