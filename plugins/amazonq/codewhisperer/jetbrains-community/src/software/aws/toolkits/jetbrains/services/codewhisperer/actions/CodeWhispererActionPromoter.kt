// Copyright 2022 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.codewhisperer.actions

import com.intellij.codeInsight.lookup.impl.actions.ChooseItemAction
import com.intellij.openapi.actionSystem.ActionPromoter
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.actionSystem.EditorAction
import software.aws.toolkits.jetbrains.services.amazonq.CodeWhispererFeatureConfigService
import software.aws.toolkits.jetbrains.services.codewhisperer.popup.handlers.CodeWhispererPopupLeftArrowHandler
import software.aws.toolkits.jetbrains.services.codewhisperer.popup.handlers.CodeWhispererPopupRightArrowHandler
import software.aws.toolkits.jetbrains.services.codewhisperer.popup.handlers.CodeWhispererPopupTabHandler
import software.aws.toolkits.jetbrains.services.codewhisperer.service.CodeWhispererInvocationStatusNew

class CodeWhispererActionPromoter : ActionPromoter {
    override fun promote(actions: MutableList<out AnAction>, context: DataContext): MutableList<AnAction> {
        if (CodeWhispererFeatureConfigService.getInstance().getNewAutoTriggerUX()) {
            val results = actions.toMutableList()
            if (!CodeWhispererInvocationStatusNew.getInstance().isDisplaySessionActive()) return results

            results.sortWith { a, b ->
                if (isCodeWhispererForceAction(a)) {
                    return@sortWith -1
                } else if (isCodeWhispererForceAction(b)) {
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
        val results = actions.toMutableList()
        results.sortWith { a, b ->
            if (isCodeWhispererPopupAction(a)) {
                return@sortWith -1
            } else if (isCodeWhispererPopupAction(b)) {
                return@sortWith 1
            } else {
                0
            }
        }
        return results
    }

    private fun isCodeWhispererAcceptAction(action: AnAction): Boolean =
        if (CodeWhispererFeatureConfigService.getInstance().getNewAutoTriggerUX()) {
            action is CodeWhispererAcceptAction
        } else {
            action is EditorAction && action.handler is CodeWhispererPopupTabHandler
        }

    private fun isCodeWhispererForceAcceptAction(action: AnAction): Boolean =
        action is CodeWhispererForceAcceptAction

    private fun isCodeWhispererNavigateAction(action: AnAction): Boolean =
        if (CodeWhispererFeatureConfigService.getInstance().getNewAutoTriggerUX()) {
            action is CodeWhispererNavigateNextAction || action is CodeWhispererNavigatePrevAction
        } else {
            action is EditorAction && (
                action.handler is CodeWhispererPopupRightArrowHandler ||
                    action.handler is CodeWhispererPopupLeftArrowHandler
                )
        }

    private fun isCodeWhispererPopupAction(action: AnAction): Boolean =
        isCodeWhispererAcceptAction(action) || isCodeWhispererNavigateAction(action)

    private fun isCodeWhispererForceAction(action: AnAction): Boolean =
        isCodeWhispererForceAcceptAction(action) || isCodeWhispererNavigateAction(action)
}
