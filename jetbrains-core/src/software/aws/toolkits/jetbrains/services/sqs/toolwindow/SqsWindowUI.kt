// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.sqs.toolwindow

import com.intellij.ui.components.JBTabbedPane
import com.intellij.util.ui.JBUI
import software.amazon.awssdk.services.sqs.SqsClient
import software.aws.toolkits.jetbrains.services.sqs.Queue
import software.aws.toolkits.resources.message
import javax.swing.event.ChangeListener

// Will add more parameters once window is populated
class SqsWindowUI(
    private val client: SqsClient,
    val queue: Queue
) {
    private val pane = PollMessagePane(client, queue)
    // This makes sure that PollMessagePane is properly set up when switched to the Poll Message tab
    private val changeListener = ChangeListener { e ->
        val selected = e?.source as JBTabbedPane
        if ((selected.selectedIndex == POLL_MESSAGE_PANE) && (!pane.isSetup)) {
            pane.setUp()
        }
    }

    val mainPanel = JBTabbedPane().apply {
        tabComponentInsets = JBUI.emptyInsets()
        border = JBUI.Borders.empty()
        add(message("sqs.queue.polled.messages"), pane.component)
        add(message("sqs.send.message"), SendMessagePane().component)
        addChangeListener(changeListener)
    }

    fun pollMessage() {
        mainPanel.selectedIndex = POLL_MESSAGE_PANE
        pane.setUp()
    }

    fun sendMessage() {
        mainPanel.selectedIndex = SEND_MESSAGE_PANE
    }

    companion object {
        const val POLL_MESSAGE_PANE = 0
        const val SEND_MESSAGE_PANE = 1
    }
}
