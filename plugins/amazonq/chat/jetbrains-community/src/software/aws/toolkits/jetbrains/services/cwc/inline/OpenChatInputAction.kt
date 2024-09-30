// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cwc.inline

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import org.jetbrains.plugins.terminal.exp.TerminalDataContextUtils.editor

class OpenChatInputAction  : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        e.editor?.project?.let { InlineChatController(e.editor!!, it).initPopup() }
    }
}
