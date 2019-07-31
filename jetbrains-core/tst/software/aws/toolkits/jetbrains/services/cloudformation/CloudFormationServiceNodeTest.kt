// Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cloudformation

import com.intellij.testFramework.ProjectRule
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.whenever
import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Rule
import org.junit.Test
import software.amazon.awssdk.services.cloudformation.CloudFormationClient
import software.amazon.awssdk.services.cloudformation.model.ListStacksRequest
import software.amazon.awssdk.services.cloudformation.model.ListStacksResponse
import software.amazon.awssdk.services.cloudformation.model.StackStatus
import software.amazon.awssdk.services.cloudformation.model.StackSummary
import software.amazon.awssdk.services.cloudformation.paginators.ListStacksIterable
import software.aws.toolkits.jetbrains.core.AwsResourceCache
import software.aws.toolkits.jetbrains.core.MockClientManagerRule
import software.aws.toolkits.jetbrains.core.explorer.nodes.AwsExplorerEmptyNode

class CloudFormationServiceNodeTest {

    @JvmField
    @Rule
    val projectRule = ProjectRule()

    @JvmField
    @Rule
    val mockClientManagerRule = MockClientManagerRule(projectRule)

    private val cfnClient by lazy { mockClientManagerRule.create<CloudFormationClient>() }

    @After
    fun tearDown() {
        AwsResourceCache.getInstance(projectRule.project).clear()
    }

    @Test
    fun completedStacksAreShown() {
         cfnClient.stacksWithNames(listOf("Stack" to StackStatus.CREATE_COMPLETE))

        val node = CloudFormationServiceNode(projectRule.project)

        assertThat(node.children).hasOnlyOneElementSatisfying { assertThat(it.displayName()).isEqualTo("Stack") }
    }

    @Test
    fun deletedStacksAreNotShown() {
        cfnClient.stacksWithNames(listOf("Stack" to StackStatus.DELETE_COMPLETE))

        val node = CloudFormationServiceNode(projectRule.project)

        assertThat(node.children).hasOnlyElementsOfType(AwsExplorerEmptyNode::class.java)
    }

    @Test
    fun noStacksShowsEmptyNode() {
        cfnClient.stacksWithNames(emptyList())

        val node = CloudFormationServiceNode(projectRule.project)

        assertThat(node.children).hasOnlyElementsOfType(AwsExplorerEmptyNode::class.java)
    }

    private fun CloudFormationClient.stacksWithNames(names: List<Pair<String, StackStatus>>) {
        whenever(listStacksPaginator(any<ListStacksRequest>())).thenReturn(
            ListStacksIterable(
                this,
                ListStacksRequest.builder().build()
            )
        )
        whenever(listStacks(any<ListStacksRequest>())).thenReturn(
            ListStacksResponse.builder()
                .stackSummaries(
                    names.map {
                        StackSummary.builder()
                            .stackName(it.first)
                            .stackId(it.first)
                            .stackStatus(it.second)
                            .build()
                    }
                ).build()
        )
    }
}