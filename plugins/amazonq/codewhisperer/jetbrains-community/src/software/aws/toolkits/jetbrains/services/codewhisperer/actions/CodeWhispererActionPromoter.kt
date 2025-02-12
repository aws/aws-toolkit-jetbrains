// Copyright 2022 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.codewhisperer.actions

import com.intellij.openapi.actionSystem.ActionPromoter
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.DataContext
import software.aws.toolkits.jetbrains.services.codewhisperer.service.CodeWhispererInvocationStatus
import software.aws.toolkits.jetbrains.services.codewhisperer.service.CodeWhispererInvocationStatusNew

class CodeWhispererActionPromoter : ActionPromoter {
    override fun promote(actions: MutableList<out AnAction>, context: DataContext): MutableList<AnAction> {
        val results = actions.toMutableList()
        if (!CodeWhispererInvocationStatusNew.getInstance().isDisplaySessionActive() &&
            !CodeWhispererInvocationStatus.getInstance().isDisplaySessionActive()
        ) {
            return results
        }

        results.sortWith { a, b ->
            if (isCodeWhispererForceAction(a)) {
                return@sortWith -1
            } else if (isCodeWhispererForceAction(b)) {
                return@sortWith 1
            }

            if (isCodeWhispererAcceptAction(a)) {
                return@sortWith -1
            } else if (isCodeWhispererAcceptAction(b)) {
                return@sortWith 1
            }

            0
        }

        results.sortWith { a, b ->
            if (isCodeWhispererNavigateAction(a)) {
                return@sortWith -1
            } else if (isCodeWhispererNavigateAction(b)) {
                return@sortWith 1
            }

            0
        }
        return results
    }

    private fun isCodeWhispererAcceptAction(action: AnAction): Boolean = action is CodeWhispererAcceptAction

    private fun isCodeWhispererForceAcceptAction(action: AnAction): Boolean =
        action is CodeWhispererForceAcceptAction

    private fun isCodeWhispererNavigateAction(action: AnAction): Boolean =
        action is CodeWhispererNavigateNextAction || action is CodeWhispererNavigatePrevAction

    private fun isCodeWhispererForceAction(action: AnAction): Boolean =
        isCodeWhispererForceAcceptAction(action) || isCodeWhispererNavigateAction(action)
}
