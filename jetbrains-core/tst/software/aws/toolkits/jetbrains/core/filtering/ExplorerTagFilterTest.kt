// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.core.filtering

import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.testFramework.ProjectRule
import com.nhaarman.mockitokotlin2.mock
import org.assertj.core.api.Assertions.assertThat
import org.junit.Rule
import org.junit.Test
import software.amazon.awssdk.services.resourcegroupstaggingapi.model.ResourceTagMapping
import software.amazon.awssdk.services.resourcegroupstaggingapi.model.Tag
import software.aws.toolkits.core.utils.RuleUtils
import software.aws.toolkits.jetbrains.core.MockResourceCacheRule
import software.aws.toolkits.jetbrains.core.explorer.nodes.AwsExplorerNode
import software.aws.toolkits.jetbrains.core.explorer.nodes.AwsExplorerResourceNode
import software.aws.toolkits.jetbrains.services.resourcegroupstaggingapi.resources.ResourceGroupsTaggingApiResources
import java.util.concurrent.CompletableFuture

class ExplorerTagFilterTest {
    @JvmField
    @Rule
    val projectRule = ProjectRule()

    @JvmField
    @Rule
    val mockResourceCache = MockResourceCacheRule(projectRule)

    val filter = ExplorerTagFilter()

    val parentName = RuleUtils.randomName()
    val serviceId = RuleUtils.randomName()
    val resourceType = RuleUtils.randomName()

    val parent = object : AwsExplorerNode<String>(projectRule.project, parentName, null) {
        override fun getChildren(): MutableCollection<out AbstractTreeNode<*>> = mutableListOf()
    }

    @Test
    fun `Does not filter if no filters are enabled`() {
        ResourceFilterManager.getInstance(projectRule.project).state["default"] = TagFilter(
            enabled = false,
            tagKey = "tag",
            tagValues = listOf()
        )
        assertThat(
            filter.modify(
                parent,
                mutableListOf(
                    buildChild(),
                    buildChild()
                ),
                mock()
            )
        ).hasSize(2)
    }

    @Test
    fun `Does not filter if parent is not AWS Explorer node`() {
        ResourceFilterManager.getInstance(projectRule.project).state["default"] = TagFilter(
            enabled = true,
            tagKey = "tag",
            tagValues = listOf()
        )
        assertThat(
            filter.modify(
                mock(),
                mutableListOf(
                    buildChild(),
                    buildChild()
                ),
                mock()
            )
        ).hasSize(2)
    }

    @Test
    fun `AWS nodes are filtered if enabled`() {
        val foundArn = RuleUtils.randomName()

        stockResourceCache(foundArn)
        ResourceFilterManager.getInstance(projectRule.project).state["default"] = TagFilter(
            enabled = true,
            tagKey = "tag",
            tagValues = listOf()
        )
        val list = filter.modify(
            parent,
            mutableListOf(
                buildChild(arn = "${foundArn}123"),
                buildChild(arn = foundArn)
            ),
            mock()
        )
        assertThat(list).hasOnlyOneElementSatisfying {
            it is AwsExplorerResourceNode && it.resourceArn() == foundArn
        }
    }

    @Test
    fun `Does not filter out non AWS resource nodes`() {
        val foundArn = RuleUtils.randomName()
        stockResourceCache(foundArn)
        ResourceFilterManager.getInstance(projectRule.project).state["default"] = TagFilter(
            enabled = true,
            tagKey = "tag",
            tagValues = listOf()
        )
        val list = filter.modify(
            parent,
            mutableListOf(
                buildChild(arn = "${foundArn}123"),
                buildChild(arn = foundArn),
                mock()
            ),
            mock()
        )
        assertThat(list).hasSize(2)
    }

    private fun buildChild(arn: String = RuleUtils.randomName()): AwsExplorerResourceNode<String> =
        object : AwsExplorerResourceNode<String>(projectRule.project, serviceId, "value", mock()) {
            override fun resourceType(): String = resourceType
            override fun resourceArn(): String = arn
        }

    private fun stockResourceCache(foundArn: String) {
        mockResourceCache.get().addEntry(
            ResourceGroupsTaggingApiResources.listResources(serviceId, resourceType),
            CompletableFuture.completedFuture(
                listOf(
                    ResourceTagMapping.builder().resourceARN(foundArn).tags(Tag.builder().key("tag").value("value").build()).build()
                )
            )
        )
    }
}
