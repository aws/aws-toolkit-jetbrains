// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.sqs.toolwindow

import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBTabbedPane
import software.aws.toolkits.jetbrains.services.sqs.Queue
import software.aws.toolkits.resources.message
import javax.swing.JPanel

// Will add more parameters once window is populated
class SqsWindowUI(private val project: Project, val queue: Queue) {
    val mainPanel = JBTabbedPane().apply {
        this.add(message("sqs.queue.message.sampling"), JPanel())
        this.add(message("sqs.queue.send.message"), JPanel())
    }

    fun openMessage(): SqsWindowUI {
        mainPanel.selectedIndex = OPEN_MESSAGE_PANE
        return this
    }

    fun sendMessage(): SqsWindowUI {
        mainPanel.selectedIndex = SEND_MESSAGE_PANE
        return this
    }

    private companion object {
        internal const val OPEN_MESSAGE_PANE = 0
        internal const val SEND_MESSAGE_PANE = 1
    }
}
