// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.core.filtering

import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.testFramework.ProjectRule
import com.nhaarman.mockitokotlin2.mock
import org.assertj.core.api.Assertions.assertThat
import org.junit.Rule
import org.junit.Test
import software.aws.toolkits.core.utils.RuleUtils
import software.aws.toolkits.jetbrains.core.MockClientManagerRule
import software.aws.toolkits.jetbrains.core.explorer.nodes.AwsExplorerNode
import software.aws.toolkits.jetbrains.core.explorer.nodes.AwsExplorerResourceNode

class ExplorerTagFilterTest {
    @JvmField
    @Rule
    val projectRule = ProjectRule()

    @JvmField
    @Rule
    val mockClientManagerRule = MockClientManagerRule(projectRule)
    val filter = ExplorerTagFilter()

    val parentName = RuleUtils.randomName()
    val parent = object : AwsExplorerNode<String>(projectRule.project, parentName, null) {
        override fun getChildren(): MutableCollection<out AbstractTreeNode<*>> = mutableListOf()
    }

    fun buildChild(
        type: String = "s3",
        arn: String = RuleUtils.randomName()
    ): AwsExplorerResourceNode<String> {
        return object : AwsExplorerResourceNode<String>() {
            override fun resourceType(): String = type
            override fun resourceArn(): String = arn
        }
    }

    @Test
    fun `Does not filter if no filters are enabled`() {
        ResourceFilterManager.getInstance(projectRule.project).state["default"] = ResourceFilter(
            enabled = false,
            tags = mapOf("tag" to listOf())
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
        ResourceFilterManager.getInstance(projectRule.project).state["default"] = ResourceFilter(
            enabled = false,
            tags = mapOf("tag" to listOf())
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

    }

    @Test
    fun `Does not filter out non AWS nodes`() {

    }
}
