// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.sqs.toolwindow

import com.intellij.testFramework.ProjectRule
import com.nhaarman.mockitokotlin2.whenever
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Rule
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
import software.aws.toolkits.jetbrains.core.MockClientManagerRule
import software.aws.toolkits.jetbrains.services.sqs.Queue
import software.aws.toolkits.resources.message

class PollMessagePaneTest {
    private lateinit var client: SqsClient
    @JvmField
    @Rule
    val projectRule = ProjectRule()

    @JvmField
    @Rule
    val mockClientManagerRule = MockClientManagerRule(projectRule)

    @Before
    fun setUp() {
        client = mockClientManagerRule.create()
    }

    @Test
    fun `Message received`() {
        whenever(client.receiveMessage(Mockito.any<ReceiveMessageRequest>())).thenReturn(
            ReceiveMessageResponse.builder().messages(message).build()
        )
        val pane = PollMessagePane(projectRule.project, client, queue)
        val model = pane.messagesTable.tableModel

        assertThat(model.items.size).isOne()
        assertThat(model.items.first().messageId()).isEqualTo("AAAAAAAAAAA")
        assertThat(model.items.first().body()).isEqualTo("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA")
        assertThat(model.items.first().attributes().getValue(MessageSystemAttributeName.SENDER_ID)).isEqualTo("1234567890:test1")
        assertThat(model.items.first().attributes().getValue(MessageSystemAttributeName.SENT_TIMESTAMP)).isEqualTo("111111111")
    }

    @Test
    fun `No messages received`() {
        whenever(client.receiveMessage(Mockito.any<ReceiveMessageRequest>())).thenReturn(
            ReceiveMessageResponse.builder().build()
        )
        val pane = PollMessagePane(projectRule.project, client, queue)

        assertThat(pane.messagesTable.tableModel.items.size).isZero()
        assertThat(pane.messagesTable.table.emptyText.text).isEqualTo(message("sqs.message.no_messages"))
    }

    @Test
    fun `Error receiving messages`() {
        whenever(client.receiveMessage(Mockito.any<ReceiveMessageRequest>())).then {
            throw IllegalStateException("Network Error")
        }
        val pane = PollMessagePane(projectRule.project, client, queue)

        assertThat(pane.messagesTable.tableModel.items.size).isZero()
        assertThat(pane.messagesTable.table.emptyText.text).isEqualTo(message("sqs.failed_to_poll_messages"))
    }

    @Test
    fun `Messages available displayed`() {
        whenever(client.getQueueAttributes(Mockito.any<GetQueueAttributesRequest>())).thenReturn(
            GetQueueAttributesResponse.builder().attributes(mutableMapOf(Pair(QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES, "10"))).build()
        )
        val pane = PollMessagePane(projectRule.project, client, queue)

        assertThat(pane.messagesAvailableLabel.text).isEqualTo("Messages Available: 10")
    }

    @Test
    fun `Error displaying messages available`() {
        whenever(client.getQueueAttributes(Mockito.any<GetQueueAttributesRequest>())).then {
            throw IllegalStateException("Network Error")
        }
        val pane = PollMessagePane(projectRule.project, client, queue)

        assertThat(pane.messagesAvailableLabel.text).isEqualTo(message("sqs.failed_to_load_total"))
    }

    private companion object {
        private val defaultRegion = AwsRegion("us-east-1", "US East (N. Virginia)", "aws")
        private val queue = Queue("https://sqs.us-east-1.amazonaws.com/123456789012/test1", defaultRegion)
        private val message: Message = Message.builder()
            .body("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA")
            .messageId("AAAAAAAAAAA")
            .attributes(mapOf(Pair(MessageSystemAttributeName.SENDER_ID, "1234567890:test1"), Pair(MessageSystemAttributeName.SENT_TIMESTAMP, "111111111")))
            .build()
    }
}
