// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.sqs.toolwindow

import com.intellij.testFramework.runInEdtAndWait
import com.nhaarman.mockitokotlin2.whenever
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito
import software.amazon.awssdk.services.sqs.SqsClient
import software.amazon.awssdk.services.sqs.model.Message
import software.amazon.awssdk.services.sqs.model.GetQueueAttributesRequest
import software.amazon.awssdk.services.sqs.model.GetQueueAttributesResponse
import software.amazon.awssdk.services.sqs.model.MessageSystemAttributeName
import software.amazon.awssdk.services.sqs.model.QueueAttributeName
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest
import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse
import software.aws.toolkits.core.region.AwsRegion
import software.aws.toolkits.jetbrains.core.region.MockRegionProvider
import software.aws.toolkits.jetbrains.services.sqs.Queue
import software.aws.toolkits.jetbrains.utils.BaseCoroutineTest
import software.aws.toolkits.resources.message
import javax.swing.JLabel

class PollMessagePaneTest : BaseCoroutineTest() {
    private lateinit var client: SqsClient
    private lateinit var region: AwsRegion
    private lateinit var table: MessagesTable
    private lateinit var label: JLabel
    private lateinit var queue: Queue

    private val message: Message = Message.builder()
        .body("ABC")
        .messageId("XYZ")
        .attributes(mapOf(Pair(MessageSystemAttributeName.SENDER_ID, "1234567890:test1"), Pair(MessageSystemAttributeName.SENT_TIMESTAMP, "111111111")))
        .build()

    @Before
    fun loadVariables() {
        client = mockClientManagerRule.create()
        region = MockRegionProvider.getInstance().defaultRegion()
        table = MessagesTable()
        queue = Queue("https://sqs.us-east-1.amazonaws.com/123456789012/test1", region)
        label = JLabel()
    }

    @Test
    fun `Message received`() {
        whenever(client.receiveMessage(Mockito.any<ReceiveMessageRequest>())).thenReturn(
            ReceiveMessageResponse.builder().messages(message).build()
        )
        runInEdtAndWait {
            table = PollMessagePane(client, queue).messagesTable
        }

        assertThat(table.tableModel.items.size).isOne()
        assertThat(table.tableModel.items.first().messageId()).isEqualTo("XYZ")
        assertThat(table.tableModel.items.first().body()).isEqualTo("ABC")
        assertThat(table.tableModel.items.first().attributes().getValue(MessageSystemAttributeName.SENDER_ID)).isEqualTo("1234567890:test1")
        assertThat(table.tableModel.items.first().attributes().getValue(MessageSystemAttributeName.SENT_TIMESTAMP)).isEqualTo("111111111")
    }

    @Test
    fun `No messages received`() {
        whenever(client.receiveMessage(Mockito.any<ReceiveMessageRequest>())).thenReturn(
            ReceiveMessageResponse.builder().build()
        )
        runInEdtAndWait {
            table = PollMessagePane(client, queue).messagesTable
        }

        assertThat(table.tableModel.items.size).isZero()
        assertThat(table.table.emptyText.text).isEqualTo(message("sqs.message.no_messages"))
    }

    @Test
    fun `Error receiving messages`() {
        whenever(client.receiveMessage(Mockito.any<ReceiveMessageRequest>())).then {
            throw IllegalStateException("Network Error")
        }
        runInEdtAndWait {
            table = PollMessagePane(client, queue).messagesTable
        }

        assertThat(table.tableModel.items.size).isZero()
        assertThat(table.table.emptyText.text).isEqualTo(message("sqs.failed_to_poll_messages"))
    }

    @Test
    fun `Available messages are displayed`() {
        whenever(client.getQueueAttributes(Mockito.any<GetQueueAttributesRequest>())).thenReturn(
            GetQueueAttributesResponse.builder().attributes(mutableMapOf(Pair(QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES, "10"))).build()
        )
        runInEdtAndWait {
            label = PollMessagePane(client, queue).messagesAvailableLabel
        }

        assertThat(label.text).isEqualTo("Messages Available: 10")
    }

    @Test
    fun `Error displaying messages available`() {
        whenever(client.getQueueAttributes(Mockito.any<GetQueueAttributesRequest>())).then {
            throw IllegalStateException("Network Error")
        }
        runInEdtAndWait {
            label = PollMessagePane(client, queue).messagesAvailableLabel
        }

        assertThat(label.text).isEqualTo(message("sqs.failed_to_load_total"))
    }
}
