// Copyright 2023 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonq.toolwindow

import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.ex.ToolWindowManagerListener
import migration.software.aws.toolkits.jetbrains.core.credentials.ToolkitAuthManager
import software.aws.toolkits.jetbrains.core.credentials.ToolkitConnectionManager
import software.aws.toolkits.jetbrains.core.credentials.pinning.QConnection
import software.aws.toolkits.jetbrains.services.cwc.controller.chat.telemetry.TelemetryHelper
import software.aws.toolkits.telemetry.AuthTelemetry

class AmazonQToolWindowListener : ToolWindowManagerListener {

    override fun toolWindowShown(toolWindow: ToolWindow) {
        TelemetryHelper.recordOpenChat()
    }

    override fun stateChanged(toolWindowManager: ToolWindowManager, event: ToolWindowManagerListener.ToolWindowManagerEventType) {
        val toolWindow = toolWindowManager.getToolWindow(AmazonQToolWindowFactory.WINDOW_ID)
        toolWindow?.let {
            if (event == ToolWindowManagerListener.ToolWindowManagerEventType.HideToolWindow && !it.isVisible) {
                TelemetryHelper.recordCloseChat()
            }
        }
    }
}
