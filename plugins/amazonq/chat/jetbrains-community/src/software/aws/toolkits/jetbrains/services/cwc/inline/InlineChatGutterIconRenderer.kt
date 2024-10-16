// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cwc.inline

import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.editor.markup.GutterIconRenderer
import javax.swing.Icon

class InlineChatGutterIconRenderer (private val icon: Icon) : GutterIconRenderer() {
    private var clickAction: (() -> Unit)? = null
    override fun equals(other: Any?): Boolean {
        if (other is InlineChatGutterIconRenderer) {
            return icon == other.icon
        }
        return false
    }

    override fun hashCode(): Int = icon.hashCode()

    override fun getIcon(): Icon = icon

    override fun getTooltipText(): String = "Amazon Q"

    override fun isNavigateAction(): Boolean = false

    override fun getClickAction(): AnAction = object : AnAction() {
        override fun actionPerformed(e: AnActionEvent) = clickAction?.invoke() ?: Unit
        override fun update(e: AnActionEvent) = Unit
    }

    fun setClickAction (action: () -> Unit) {
        clickAction = action
    }

    override fun getPopupMenuActions(): ActionGroup? = null

    override fun getAlignment(): Alignment = Alignment.CENTER
}

