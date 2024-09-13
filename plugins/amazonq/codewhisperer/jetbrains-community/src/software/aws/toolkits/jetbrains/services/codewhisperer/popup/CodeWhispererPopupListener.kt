// Copyright 2022 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.codewhisperer.popup

import com.intellij.openapi.ui.popup.JBPopupListener
import com.intellij.openapi.ui.popup.LightweightWindowEvent
import software.aws.toolkits.jetbrains.services.codewhisperer.service.CodeWhispererService

class CodeWhispererPopupListener : JBPopupListener {
    override fun onClosed(event: LightweightWindowEvent) {
        super.onClosed(event)
        CodeWhispererService.getInstance().disposeDisplaySession(event.isOk)
    }
}
