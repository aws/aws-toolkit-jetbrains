// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.codewhisperer.popup

import com.intellij.codeInsight.inline.completion.session.InlineCompletionSession
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.project.DumbAware
import com.intellij.util.ui.JBUI

fun navigationButton(session: InlineCompletionSession, direction: String): ActionButton {
    val icon = if (direction == "←") AllIcons.Chooser.Left else AllIcons.Chooser.Right
    val navigate = if (direction == "←") session::usePrevVariant else session::useNextVariant
    return object : ActionButton(
        object : AnAction(direction, null, icon), DumbAware {
            override fun actionPerformed(e: AnActionEvent) {
                navigate()
            }
        },
        Presentation().apply {
            this.icon = icon
            putClientProperty(ActionUtil.HIDE_DROPDOWN_ICON, true)
        },
        ActionPlaces.EDITOR_POPUP,
        JBUI.emptySize()
    ) {
        override fun isFocusable() = false
    }
}
