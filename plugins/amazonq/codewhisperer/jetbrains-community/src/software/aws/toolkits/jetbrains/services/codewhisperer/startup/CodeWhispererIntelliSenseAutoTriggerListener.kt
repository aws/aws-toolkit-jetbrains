// Copyright 2022 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.codewhisperer.startup

import com.intellij.codeInsight.lookup.Lookup
import com.intellij.codeInsight.lookup.LookupEvent
import com.intellij.codeInsight.lookup.LookupListener
import com.intellij.codeInsight.lookup.LookupManagerListener
import com.intellij.codeInsight.lookup.impl.LookupImpl
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.observable.util.addMouseHoverListener
import com.intellij.ui.hover.HoverListener
import software.aws.toolkits.jetbrains.services.amazonq.CodeWhispererFeatureConfigService
import software.aws.toolkits.jetbrains.services.codewhisperer.service.CodeWhispererAutoTriggerService
import software.aws.toolkits.jetbrains.services.codewhisperer.service.CodeWhispererAutomatedTriggerType
import software.aws.toolkits.jetbrains.services.codewhisperer.service.CodeWhispererServiceNew
import java.awt.Component

object CodeWhispererIntelliSenseAutoTriggerListener : LookupManagerListener {
    override fun activeLookupChanged(oldLookup: Lookup?, newLookup: Lookup?) {
        if (oldLookup != null || newLookup == null) return

        newLookup.addLookupListener(object : LookupListener {
            override fun itemSelected(event: LookupEvent) {
                val editor = event.lookup.editor
                if (!(event.lookup as LookupImpl).isShown) {
                    cleanup()
                    return
                }

                // Classifier
                CodeWhispererAutoTriggerService.getInstance().tryInvokeAutoTrigger(editor, CodeWhispererAutomatedTriggerType.IntelliSense())
                cleanup()
            }
            override fun lookupCanceled(event: LookupEvent) {
                cleanup()
            }

            private fun cleanup() {
                newLookup.removeLookupListener(this)
            }
        })

        if (CodeWhispererFeatureConfigService.getInstance().getNewAutoTriggerUX()) {
            (newLookup as LookupImpl).component.addMouseHoverListener(
                newLookup,
                object : HoverListener() {
                    override fun mouseEntered(component: Component, x: Int, y: Int) {
                        runReadAction {
                            newLookup.project.messageBus.syncPublisher(
                                CodeWhispererServiceNew.CODEWHISPERER_INTELLISENSE_POPUP_ON_HOVER,
                            ).onEnter()
                        }
                    }
                    override fun mouseMoved(component: Component, x: Int, y: Int) {}
                    override fun mouseExited(component: Component) {}
                }
            )
        }
    }
}
