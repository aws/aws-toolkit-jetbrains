// Copyright 2023 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.core.explorer.filters

import com.intellij.ide.projectView.PresentationData
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.testFramework.ProjectRule
import org.assertj.core.api.Assertions.assertThat
import org.junit.Rule
import org.junit.Test
import software.aws.toolkits.core.utils.test.hasOnlyElementsOfType
import software.aws.toolkits.jetbrains.core.explorer.nodes.AwsExplorerNode
import software.aws.toolkits.jetbrains.core.explorer.nodes.AwsExplorerRootNode

class AwsExplorerFilterProcessorTest {

    @Rule
    @JvmField
    val projectRule = ProjectRule()

    @Test
    fun filterApplied() {
        AwsExplorerFilterManager.getInstance(projectRule.project).setFilter(TestFilter(listOf("foo", "baz")))

        val sut = AwsExplorerFilterProcessor()

        val foo = node("foo")
        val baz = node("baz")
        val result = sut.modify(node("parent"), mutableListOf(foo, node("bar"), baz))

        assertThat(result).containsExactly(foo, baz)
    }

    @Test
    fun onlyAwsExplorerNodesAreFiltered() {
        AwsExplorerFilterManager.getInstance(projectRule.project).setFilter(TestFilter(listOf("foo")))

        val sut = AwsExplorerFilterProcessor()

        val foo = node("foo")
        val nonExplorerNode = MockTreeNode()
        val result = sut.modify(node("parent"), mutableListOf(foo, node("bar"), nonExplorerNode, node("baz")))

        assertThat(result).containsExactly(foo, nonExplorerNode)
    }

    @Test
    fun aFilterInfoNodeIsAddedIfParentIsRoot() {
        AwsExplorerFilterManager.getInstance(projectRule.project).setFilter(TestFilter(listOf("foo")))

        val sut = AwsExplorerFilterProcessor()

        val foo = node("foo")
        val result = sut.modify(AwsExplorerRootNode(projectRule.project), mutableListOf(foo, node("bar")))
        assertThat(result).hasAtLeastOneElementOfType(AwsExplorerFilterNode::class.java).contains(foo)
    }

    @Test
    fun aFilterInfoNodeIsNotAddedIfNoFilterApplied() {
        val sut = AwsExplorerFilterProcessor()

        val result = sut.modify(AwsExplorerRootNode(projectRule.project), mutableListOf(node("foo"), node("bar")))
        assertThat(result).hasOnlyElementsOfType<MockExplorerNode>().hasSize(2)
    }

    private class TestFilter(private val idsToShow: List<String>) : AwsExplorerFilter {
        override fun displayName() = ""
        override fun show(node: AwsExplorerNode<*>) = node is MockExplorerNode && node.id in idsToShow
    }

    private fun node(id: String) = MockExplorerNode(id)

    private inner class MockExplorerNode(val id: String) : AwsExplorerNode<String>(projectRule.project, id, null) {
        override fun getChildren(): MutableCollection<out AbstractTreeNode<*>> = mutableListOf()
    }

    private inner class MockTreeNode : AbstractTreeNode<String>(projectRule.project, "other") {
        override fun update(presentation: PresentationData) {}
        override fun getChildren(): MutableCollection<out AbstractTreeNode<*>> = mutableListOf()
    }
}
