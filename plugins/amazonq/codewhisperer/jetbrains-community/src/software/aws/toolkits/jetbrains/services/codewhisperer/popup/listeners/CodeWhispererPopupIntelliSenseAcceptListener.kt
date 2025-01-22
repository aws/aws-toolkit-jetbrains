// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.codewhisperer.popup.listeners

import com.intellij.codeInsight.lookup.Lookup
import com.intellij.codeInsight.lookup.LookupEvent
import com.intellij.codeInsight.lookup.LookupListener
import com.intellij.codeInsight.lookup.LookupManagerListener
import com.intellij.codeInsight.lookup.impl.LookupImpl
import software.aws.toolkits.jetbrains.services.codewhisperer.model.InvocationContext
import software.aws.toolkits.jetbrains.services.codewhisperer.popup.CodeWhispererPopupManager
import software.aws.toolkits.jetbrains.services.codewhisperer.service.CodeWhispererInvocationStatus

class CodeWhispererPopupIntelliSenseAcceptListener(private val states: InvocationContext) : LookupManagerListener {
    override fun activeLookupChanged(oldLookup: Lookup?, newLookup: Lookup?) {
        if (oldLookup != null || newLookup == null) return

        addIntelliSenseAcceptListener(newLookup, states)
    }
}

fun addIntelliSenseAcceptListener(lookup: Lookup, states: InvocationContext) {
    lookup.addLookupListener(object : LookupListener {
        override fun beforeItemSelected(event: LookupEvent): Boolean {
            CodeWhispererPopupManager.getInstance().shouldListenerCancelPopup = false
            return super.beforeItemSelected(event)
        }
        override fun itemSelected(event: LookupEvent) {
            if (!CodeWhispererInvocationStatus.getInstance().isDisplaySessionActive() ||
                !(event.lookup as LookupImpl).isShown
            ) {
                cleanup()
                return
            }
            CodeWhispererPopupManager.getInstance().changeStates(
                states,
                0
            )
            cleanup()
        }

        override fun lookupCanceled(event: LookupEvent) {
            cleanup()
        }

        private fun cleanup() {
            lookup.removeLookupListener(this)
            CodeWhispererPopupManager.getInstance().shouldListenerCancelPopup = true
        }
    })
}
