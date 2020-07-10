// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.sqs

import com.intellij.testFramework.ProjectRule
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import software.aws.toolkits.jetbrains.core.MockResourceCache
import software.aws.toolkits.jetbrains.core.explorer.nodes.AwsExplorerEmptyNode
import software.aws.toolkits.jetbrains.core.explorer.nodes.AwsExplorerErrorNode
import software.aws.toolkits.jetbrains.core.explorer.nodes.SqsExplorerRootNode
import software.aws.toolkits.jetbrains.services.sqs.resources.SqsResources
import java.util.concurrent.CompletableFuture

class SqsServiceNodeTest {
    @JvmField
    @Rule
    val projectRule = ProjectRule()

    @Before
    fun setUp() {
        resourceCache().clear()
    }

    @Test
    fun sqsQueuesAreListed() {
        val queueList = listOf(
            "https://sqs.us-east-1.amazonaws.com/123456789012/test2",
            "https://sqs.us-east-1.amazonaws.com/123456789012/test4",
            "https://sqs.us-east-1.amazonaws.com/123456789012/test3",
            "https://sqs.us-east-1.amazonaws.com/123456789012/test1"
        )
        resourceCache().sqsQueues(queueList)
        val children = SqsServiceNode(projectRule.project, SQS_EXPLORER_NODE).children

        assertThat(children).allMatch { it is SqsQueueNode }
        assertThat(children.filterIsInstance<SqsQueueNode>().map {Queue(it.queueUrl).queueName}).containsExactlyInAnyOrder("test4", "test3", "test2", "test1")
        assertThat(children.filterIsInstance<SqsQueueNode>().map {Queue(it.queueUrl).arn}).containsExactlyInAnyOrder(
            "arn:aws:sqs:us-east-1:123456789012:test1",
            "arn:aws:sqs:us-east-1:123456789012:test2",
            "arn:aws:sqs:us-east-1:123456789012:test3",
            "arn:aws:sqs:us-east-1:123456789012:test4")
    }

    @Test
    fun noQueuesListed() {
        resourceCache().sqsQueues(emptyList())

        val children = SqsServiceNode(projectRule.project, SQS_EXPLORER_NODE).children

        assertThat(children).hasSize(1)
        assertThat(children).allMatch { it is AwsExplorerEmptyNode }
    }

    @Test
    fun errorLoadingQueues() {
        resourceCache().addEntry(SqsResources.LIST_QUEUE_URLS, CompletableFuture<List<String>>().also {
            it.completeExceptionally(RuntimeException("Simulated error"))
        })
        val children = SqsServiceNode(projectRule.project, SQS_EXPLORER_NODE).children
        assertThat(children).allMatch { it is AwsExplorerErrorNode }
    }


    private fun resourceCache() = MockResourceCache.getInstance(projectRule.project)

    private fun MockResourceCache.sqsQueues(queueUrls: List<String>) {
        this.addEntry(
            SqsResources.LIST_QUEUE_URLS,
            CompletableFuture.completedFuture(queueUrls.map{it}))
    }

    private companion object {
        val SQS_EXPLORER_NODE = SqsExplorerRootNode()
    }
}
