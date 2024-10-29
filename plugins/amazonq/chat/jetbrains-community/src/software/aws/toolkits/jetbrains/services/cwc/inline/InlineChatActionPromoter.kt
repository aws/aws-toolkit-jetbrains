// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cwc.inline

import com.intellij.openapi.actionSystem.ActionPromoter
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.util.SystemInfo

class InlineChatActionPromoter : ActionPromoter {
    // temporary until we find a better key binding
    override fun promote(actions: MutableList<out AnAction>, context: DataContext): MutableList<AnAction> {
        val results = actions.toMutableList()
        if (context.getData(CommonDataKeys.EDITOR) == null || context.getData(CommonDataKeys.PROJECT) == null) return results
        val shortCut = KeymapUtil.getShortcutText("aws.toolkit.jetbrains.core.services.cwc.inline.openChat")
        // only promote for the default key bindings
        if (SystemInfo.isMac && shortCut != "⌘I") return results
        if (!SystemInfo.isMac && shortCut != "Ctrl+I") return results

        results.sortWith { a, b ->
            when {
                isOpenChatInputAction(a) -> -1
                isOpenChatInputAction(b) -> 1
                else -> 0
            }
        }
        return results
    }

    private fun isOpenChatInputAction(action: AnAction): Boolean =
        action is OpenChatInputAction
}
