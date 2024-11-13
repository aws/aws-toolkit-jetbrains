// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cwc.inline

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.util.Key

class OpenChatInputAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        // this is a hack since this key does not exist in 233
        // TODO: change below to use ConsoleViewImpl.IS_CONSOLE_DOCUMENT after 233 support is removed
        var isConsole: Any? = null
        val key: Key<*>? = Key.findKeyByName("IS_CONSOLE_DOCUMENT")
        if (key != null) {
            isConsole = editor.document.getUserData(key)
        }
        if (isConsole == true) return
        if (!editor.document.isWritable) return
        val project = editor.project ?: return

        val inlineChatController = InlineChatController.getInstance(project)
        inlineChatController.initPopup(editor)
    }
}
