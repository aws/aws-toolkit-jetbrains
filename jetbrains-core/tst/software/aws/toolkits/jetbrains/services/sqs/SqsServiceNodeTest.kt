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
        val queueList = listOf("bcd", "abc", "zzz", "AEF")
        resourceCache().sqsQueues(queueList)
        val children = SqsServiceNode(projectRule.project, SQS_EXPLORER_NODE).children

        assertThat(children).allMatch { it is SqsQueueNode }
        assertThat(children.filterIsInstance<SqsQueueNode>().map {it.queueUrl}).containsExactlyInAnyOrder("abc", "AEF", "bcd", "zzz")
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
        resourceCache().addEntry(SqsResources.LIST_QUEUES, CompletableFuture<List<String>>().also {
            it.completeExceptionally(RuntimeException("Simulated error"))
        })
        val children = SqsServiceNode(projectRule.project, SQS_EXPLORER_NODE).children
        assertThat(children).allMatch { it is AwsExplorerErrorNode }
    }


    private fun resourceCache() = MockResourceCache.getInstance(projectRule.project)

    private fun MockResourceCache.sqsQueues(names: List<String>) {
        this.addEntry(
            SqsResources.LIST_QUEUES,
            CompletableFuture.completedFuture(names.map{it}))
    }

    private companion object {
        val SQS_EXPLORER_NODE = SqsExplorerRootNode()
    }
}
