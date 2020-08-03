// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.sqs.toolwindow

import com.intellij.testFramework.runInEdtAndWait
import com.nhaarman.mockitokotlin2.whenever
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito
import software.amazon.awssdk.services.sqs.SqsClient
import software.amazon.awssdk.services.sqs.model.SendMessageRequest
import software.aws.toolkits.core.region.AwsRegion
import software.aws.toolkits.jetbrains.core.region.MockRegionProvider
import software.aws.toolkits.jetbrains.services.sqs.Queue
import software.aws.toolkits.jetbrains.utils.BaseCoroutineTest
import software.aws.toolkits.jetbrains.utils.waitForTrue
import software.aws.toolkits.resources.message
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SendMessagePaneTest : BaseCoroutineTest() {
    private lateinit var client: SqsClient
    private lateinit var region: AwsRegion
    private lateinit var standardQueue: Queue
    private lateinit var fifoQueue: Queue

    @Before
    fun reset() {
        client = mockClientManagerRule.create()
        region = MockRegionProvider.getInstance().defaultRegion()
        standardQueue = Queue("https://sqs.us-east-1.amazonaws.com/123456789012/standard", region)
        fifoQueue = Queue("https://sqs.us-east-1.amazonaws.com/123456789012/fifo.fifo", region)
    }

    @Test
    fun `No input fails to send for standard`() {
        val pane = SendMessagePane(client, standardQueue).apply {
            runBlocking { sendMessage() }
        }

        assertTrue { pane.emptyBodyLabel.isVisible }
        assertFalse { pane.fifoComponent.isVisible }
    }

    @Test
    fun `No input fails to send for fifo`() {
        val pane = SendMessagePane(client, fifoQueue).apply {
            runBlocking { sendMessage() }
        }

        assertTrue { pane.emptyBodyLabel.isVisible }
        assertTrue { pane.emptyDeduplicationLabel.isVisible }
        assertTrue { pane.emptyGroupLabel.isVisible }
    }

    @Test
    fun `No deduplication ID fails to send for fifo`() {
        val pane = SendMessagePane(client, fifoQueue).apply {
            inputText.text = MESSAGE
            groupID.text = GROUP_ID
            runBlocking { sendMessage() }
        }

        assertFalse { pane.emptyBodyLabel.isVisible }
        assertTrue { pane.emptyDeduplicationLabel.isVisible }
        assertFalse { pane.emptyGroupLabel.isVisible }
    }

    @Test
    fun `No group ID fails to send for fifo`() {
        val pane = SendMessagePane(client, fifoQueue).apply {
            inputText.text = MESSAGE
            deduplicationID.text = DEDUPLICATION_ID
            runBlocking { sendMessage() }
        }

        assertFalse { pane.emptyBodyLabel.isVisible }
        assertFalse { pane.emptyDeduplicationLabel.isVisible }
        assertTrue { pane.emptyGroupLabel.isVisible }
    }

    /*
    @Test
    fun `Error sending message`() {
        whenever(client.sendMessage(Mockito.any<SendMessageRequest>())).then {
            throw IllegalStateException("Network Error")
        }

        val pane = SendMessagePane(client, fifoQueue).apply {
            inputText.text = MESSAGE
            runBlocking {
                sendMessage()
                waitForTrue { successPanel.isVisible }
            }
        }

        assertThat(pane.statusLabel.text).isEqualTo(message("sqs.send.message.error"))
    }*/

    private companion object {
        const val MESSAGE = "Message body"
        const val DEDUPLICATION_ID = "Deduplication ID"
        const val GROUP_ID = "Group Id"
    }
}
