// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.codewhisperer.popup.listeners

import com.intellij.codeInsight.lookup.Lookup
import com.intellij.codeInsight.lookup.LookupEvent
import com.intellij.codeInsight.lookup.LookupListener
import com.intellij.codeInsight.lookup.LookupManagerListener
import com.intellij.codeInsight.lookup.impl.LookupImpl
import software.aws.toolkits.core.utils.error
import software.aws.toolkits.core.utils.getLogger
import software.aws.toolkits.jetbrains.services.codewhisperer.model.InvocationContext
import software.aws.toolkits.jetbrains.services.codewhisperer.popup.CodeWhispererPopupManager
import software.aws.toolkits.jetbrains.services.codewhisperer.popup.listeners.CodeWhispererPopupIntelliSenseAcceptListener.Companion.LOG
import software.aws.toolkits.jetbrains.services.codewhisperer.service.CodeWhispererInvocationStatus

class CodeWhispererPopupIntelliSenseAcceptListener(private val states: InvocationContext) : LookupManagerListener {
    override fun activeLookupChanged(oldLookup: Lookup?, newLookup: Lookup?) {
        if (oldLookup != null || newLookup == null) return

        addIntelliSenseAcceptListener(newLookup, states)
    }

    companion object {
        val LOG = getLogger<CodeWhispererPopupIntelliSenseAcceptListener>()
    }
}

fun addIntelliSenseAcceptListener(lookup: Lookup, states: InvocationContext) {
    if (!CodeWhispererPopupManager.getInstance().allowEditsDuringSuggestionPreview.tryAcquire()) {
        LOG.error { "Failed to acquire allowEditsDuringSuggestionPreview semaphore" }
    }
    lookup.addLookupListener(object : LookupListener {
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
            try {
                CodeWhispererPopupManager.getInstance().allowEditsDuringSuggestionPreview.release()
            } catch (e: Exception) {
                LOG.error(e) { "Failed to release allowEditsDuringSuggestionPreview semaphore" }
            }
        }
    })
}
