// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.sqs.toolwindow

import com.nhaarman.mockitokotlin2.whenever
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito
import software.amazon.awssdk.services.sqs.SqsClient
import software.amazon.awssdk.services.sqs.model.SendMessageRequest
import software.amazon.awssdk.services.sqs.model.SendMessageResponse
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
    private lateinit var standardPane: SendMessagePane
    private lateinit var fifoPane: SendMessagePane

    @Before
    fun reset() {
        client = mockClientManagerRule.create()
        region = MockRegionProvider.getInstance().defaultRegion()
        standardQueue = Queue("https://sqs.us-east-1.amazonaws.com/123456789012/standard", region)
        fifoQueue = Queue("https://sqs.us-east-1.amazonaws.com/123456789012/fifo.fifo", region)
        standardPane = SendMessagePane(client, standardQueue)
        fifoPane = SendMessagePane(client, fifoQueue)
    }

    @Test
    fun `No input fails to send for standard`() {
        standardPane.apply {
            inputText.text = ""
            runBlocking { sendMessage() }
        }

        assertTrue { standardPane.emptyBodyLabel.isVisible }
        assertFalse { standardPane.fifoComponent.isVisible }
    }

    @Test
    fun `No input fails to send for fifo`() {
        fifoPane.apply {
            inputText.text = ""
            deduplicationId.text = ""
            groupId.text = ""
            runBlocking { sendMessage() }
        }

        assertTrue { fifoPane.emptyBodyLabel.isVisible }
        assertTrue { fifoPane.emptyDeduplicationLabel.isVisible }
        assertTrue { fifoPane.emptyGroupLabel.isVisible }
    }

    @Test
    fun `No deduplication ID fails to send for fifo`() {
        fifoPane.apply {
            inputText.text = MESSAGE
            deduplicationId.text = ""
            groupId.text = GROUP_ID
            runBlocking { sendMessage() }
        }

        assertFalse { fifoPane.emptyBodyLabel.isVisible }
        assertTrue { fifoPane.emptyDeduplicationLabel.isVisible }
        assertFalse { fifoPane.emptyGroupLabel.isVisible }
    }

    @Test
    fun `No group ID fails to send for fifo`() {
        fifoPane.apply {
            inputText.text = MESSAGE
            deduplicationId.text = DEDUPLICATION_ID
            groupId.text = ""
            runBlocking { sendMessage() }
        }

        assertFalse { fifoPane.emptyBodyLabel.isVisible }
        assertFalse { fifoPane.emptyDeduplicationLabel.isVisible }
        assertTrue { fifoPane.emptyGroupLabel.isVisible }
    }

    @Test
    fun `Error sending message`() {
        whenever(client.sendMessage(Mockito.any<SendMessageRequest>())).then {
            throw IllegalStateException("Network Error")
        }

        standardPane.apply {
            inputText.text = MESSAGE
            runBlocking {
                sendMessage()
                waitForTrue { standardPane.confirmationPanel.isVisible }
            }
        }

        assertThat(standardPane.statusLabel.text).isEqualTo(message("sqs.send.message.error"))
    }

    @Test
    fun `Message sent for standard`() {
        whenever(client.sendMessage(Mockito.any<SendMessageRequest>())).thenReturn(
            SendMessageResponse.builder().messageId(MESSAGE_ID).build()
        )
        standardPane.apply {
            inputText.text = MESSAGE
            runBlocking {
                sendMessage()
                waitForTrue { standardPane.confirmationPanel.isVisible }
            }
        }

        assertThat(standardPane.statusLabel.text).isEqualTo(message("sqs.send.message.success", MESSAGE_ID))
    }

    @Test
    fun `Message sent for fifo`() {
        whenever(client.sendMessage(Mockito.any<SendMessageRequest>())).thenReturn(
            SendMessageResponse.builder().messageId(MESSAGE_ID).build()
        )
        fifoPane.apply {
            inputText.text = MESSAGE
            deduplicationId.text = DEDUPLICATION_ID
            groupId.text = GROUP_ID
            runBlocking {
                sendMessage()
                waitForTrue { fifoPane.confirmationPanel.isVisible }
            }
        }

        assertThat(fifoPane.statusLabel.text).isEqualTo(message("sqs.send.message.success", MESSAGE_ID))
    }

    private companion object {
        const val MESSAGE_ID = "123"
        const val MESSAGE = "Message body"
        const val DEDUPLICATION_ID = "Deduplication ID"
        const val GROUP_ID = "Group Id"
    }
}
