// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cwc.inline

import com.intellij.execution.impl.ConsoleViewImpl
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys

class OpenChatInputAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        val isConsole = editor.document.getUserData(ConsoleViewImpl.IS_CONSOLE_DOCUMENT)
        if (isConsole == true) return
        if (!editor.document.isWritable) return
        val project = editor.project ?: return

        val inlineChatController = InlineChatController.getInstance(project)
        inlineChatController.initPopup(editor)
    }
}
