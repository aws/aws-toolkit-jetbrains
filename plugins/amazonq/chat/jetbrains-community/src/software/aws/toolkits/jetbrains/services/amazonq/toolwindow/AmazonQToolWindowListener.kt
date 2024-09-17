// Copyright 2023 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonq.toolwindow

import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.ex.ToolWindowManagerListener
import software.aws.toolkits.jetbrains.services.cwc.controller.chat.telemetry.TelemetryHelper

class AmazonQToolWindowListener : ToolWindowManagerListener {

    override fun toolWindowShown(toolWindow: ToolWindow) {
        if (toolWindow.id == AmazonQToolWindowFactory.WINDOW_ID) {
            TelemetryHelper.recordOpenChat()
        }
    }

    override fun stateChanged(
        toolWindowManager: ToolWindowManager,
        toolWindow: ToolWindow,
        changeType: ToolWindowManagerListener.ToolWindowManagerEventType
    ) {
        if (toolWindow.id != AmazonQToolWindowFactory.WINDOW_ID) {
            return
        }

        if (changeType == ToolWindowManagerListener.ToolWindowManagerEventType.HideToolWindow) {
            TelemetryHelper.recordCloseChat()
        }
    }
}
