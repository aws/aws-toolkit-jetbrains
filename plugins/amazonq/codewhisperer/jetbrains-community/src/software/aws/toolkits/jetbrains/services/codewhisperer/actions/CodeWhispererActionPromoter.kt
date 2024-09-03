// Copyright 2022 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.codewhisperer.actions

import com.intellij.codeInsight.lookup.impl.actions.ChooseItemAction
import com.intellij.openapi.actionSystem.ActionPromoter
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.DataContext
import software.aws.toolkits.jetbrains.services.codewhisperer.service.CodeWhispererInvocationStatus
import software.aws.toolkits.jetbrains.services.codewhisperer.settings.CodeWhispererSettings

class CodeWhispererActionPromoter : ActionPromoter {
    override fun promote(actions: MutableList<out AnAction>, context: DataContext): MutableList<AnAction> {
        val results = actions.toMutableList()
        if (!CodeWhispererInvocationStatus.getInstance().isDisplaySessionActive()) return results
        if (!CodeWhispererSettings.getInstance().isQSuggestionPrioritized() &&
            !results.any { isCodeWhispererForceAction(it) }) {
            return results
        }
        results.sortWith { a, b ->
            if (isCodeWhispererForceAction(a) || isCodeWhispererAcceptActionPrioritized(a)) {
                return@sortWith -1
            } else if (isCodeWhispererForceAction(b) || isCodeWhispererAcceptActionPrioritized(b)) {
                return@sortWith 1
            }

            if (a is ChooseItemAction) {
                return@sortWith -1
            } else if (b is ChooseItemAction) {
                return@sortWith 1
            }
            
            if (isCodeWhispererAcceptAction(a)) {
                return@sortWith -1
            } else if (isCodeWhispererAcceptAction(b)) {
                return@sortWith 1
            }

            0
        }
        return results
    }

    private fun isCodeWhispererAcceptAction(action: AnAction): Boolean =
        action is CodeWhispererAcceptAction

    private fun isCodeWhispererForceAcceptAction(action: AnAction): Boolean =
        action is CodeWhispererForceAcceptAction

    private fun isCodeWhispererNavigateAction(action: AnAction): Boolean =
        action is CodeWhispererNavigateNextAction || action is CodeWhispererNavigatePrevAction

    private fun isCodeWhispererForceAction(action: AnAction): Boolean =
        isCodeWhispererForceAcceptAction(action) || isCodeWhispererNavigateAction(action)

    private fun isCodeWhispererAcceptActionPrioritized(action: AnAction): Boolean =
        action is CodeWhispererAcceptAction && CodeWhispererSettings.getInstance().isQSuggestionPrioritized()

    private fun isCodeWhispererPopupAction(action: AnAction): Boolean =
        isCodeWhispererAcceptAction(action) || isCodeWhispererNavigateAction(action)
}
