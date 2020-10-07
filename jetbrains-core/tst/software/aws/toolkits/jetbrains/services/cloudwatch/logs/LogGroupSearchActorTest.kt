// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cloudwatch.logs

import com.intellij.ui.table.TableView
import com.intellij.util.ui.ListTableModel
import com.nhaarman.mockitokotlin2.whenever
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.ClosedSendChannelException
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito
import software.amazon.awssdk.services.cloudwatchlogs.CloudWatchLogsClient
import software.amazon.awssdk.services.cloudwatchlogs.model.DescribeLogStreamsRequest
import software.amazon.awssdk.services.cloudwatchlogs.model.DescribeLogStreamsResponse
import software.amazon.awssdk.services.cloudwatchlogs.model.LogStream
import software.aws.toolkits.jetbrains.utils.BaseCoroutineTest
import software.aws.toolkits.jetbrains.utils.waitForModelToBeAtLeast
import software.aws.toolkits.jetbrains.utils.waitForTrue
import software.aws.toolkits.resources.message
import java.time.Duration

@ExperimentalCoroutinesApi
class LogGroupSearchActorTest : BaseCoroutineTest() {
    private lateinit var client: CloudWatchLogsClient
    private lateinit var tableModel: ListTableModel<LogStream>
    private lateinit var table: TableView<LogStream>
    private lateinit var actor: CloudWatchLogsActor<LogStream>

    @Before
    fun loadVariables() {
        client = mockClientManagerRule.create()
        tableModel = ListTableModel<LogStream>()
        table = TableView(tableModel)
        actor = LogGroupSearchActor(projectRule.project, client, table, "abc")
    }

    @Test
    fun modelIsPopulated() {
        whenever(client.describeLogStreams(Mockito.any<DescribeLogStreamsRequest>()))
            .thenReturn(DescribeLogStreamsResponse.builder().logStreams(LogStream.builder().logStreamName("name-cool").build()).build())
        runBlocking {
            actor.channel.send(CloudWatchLogsActor.Message.LoadInitialFilter("name"))
            tableModel.waitForModelToBeAtLeast(1)
        }
        assertThat(tableModel.items.size).isOne()
        assertThat(tableModel.items.first().logStreamName()).isEqualTo("name-cool")
    }

    @Test
    fun loadingForwardAppendsToTable() {
        whenever(client.describeLogStreams(Mockito.any<DescribeLogStreamsRequest>()))
            .thenReturn(DescribeLogStreamsResponse.builder().logStreams(LogStream.builder().logStreamName("name-cool").build()).nextToken("token").build())
            .thenReturn(DescribeLogStreamsResponse.builder().logStreams(LogStream.builder().logStreamName("name-2").build()).build())
        runBlocking {
            actor.channel.send(CloudWatchLogsActor.Message.LoadInitialFilter("name"))
            actor.channel.send(CloudWatchLogsActor.Message.LoadForward)
            tableModel.waitForModelToBeAtLeast(2)
        }
        assertThat(tableModel.items.size).isEqualTo(2)
        assertThat(tableModel.items.first().logStreamName()).isEqualTo("name-cool")
        assertThat(tableModel.items[1].logStreamName()).isEqualTo("name-2")
    }

    @Test
    fun loadingBackwardsDoesNothing() {
        whenever(client.describeLogStreams(Mockito.any<DescribeLogStreamsRequest>()))
            .thenReturn(DescribeLogStreamsResponse.builder().logStreams(LogStream.builder().logStreamName("name-cool").build()).build())
        runBlocking {
            actor.channel.send(CloudWatchLogsActor.Message.LoadInitialFilter("name"))
            actor.channel.send(CloudWatchLogsActor.Message.LoadBackward)
            actor.channel.send(CloudWatchLogsActor.Message.LoadBackward)
            tableModel.waitForModelToBeAtLeast(1)
        }
        assertThat(tableModel.items.size).isOne()
        assertThat(tableModel.items.first().logStreamName()).isEqualTo("name-cool")
    }

    @Test
    fun writeChannelAndCoroutineIsDisposed() {
        val channel = actor.channel
        actor.dispose()
        assertThatThrownBy {
            runBlocking {
                channel.send(CloudWatchLogsActor.Message.LoadBackward)
            }
        }.isInstanceOf(ClosedSendChannelException::class.java)
    }

    @Test
    fun loadInitialThrows() {
        runBlocking {
            actor.channel.send(CloudWatchLogsActor.Message.LoadInitial)
            waitForTrue { actor.channel.isClosedForSend }
        }
    }

    @Test
    fun loadInitialRangeThrows() {
        runBlocking {
            actor.channel.send(CloudWatchLogsActor.Message.LoadInitialRange(LogStreamEntry("@@@", 0), Duration.ofMillis(0)))
            waitForTrue { actor.channel.isClosedForSend }
        }
    }

    @Test
    fun emptyTableOnExceptionThrown() {
        whenever(client.describeLogStreams(Mockito.any<DescribeLogStreamsRequest>())).thenThrow(IllegalStateException("network broke"))
        runBlocking {
            actor.channel.send(CloudWatchLogsActor.Message.LoadInitialFilter("name"))
            waitForTrue {
                println(table.emptyText.text)
                table.emptyText.text == message("cloudwatch.logs.failed_to_load_streams", "abc")
            }
        }
        assertThat(tableModel.items).isEmpty()
    }
}
