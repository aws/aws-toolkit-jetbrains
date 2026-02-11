// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cfnlsp.explorer.nodes

import com.intellij.testFramework.ProjectRule
import org.assertj.core.api.Assertions.assertThat
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import software.aws.toolkits.jetbrains.services.cfnlsp.resources.ResourceLoader
import software.aws.toolkits.jetbrains.services.cfnlsp.resources.ResourceTypesManager

class ResourcesNodeTest {

    @get:Rule
    val projectRule = ProjectRule()

    @Test
    fun `shows add resource type node when no types selected`() {
        val mockResourceTypesManager = mock<ResourceTypesManager>()
        val mockResourceLoader = mock<ResourceLoader>()

        whenever(mockResourceTypesManager.getSelectedResourceTypes()).thenReturn(emptySet())

        val node = ResourcesNode(projectRule.project, mockResourceTypesManager, mockResourceLoader)
        val children = node.children

        assertThat(children).hasSize(1) // Only AddResourceTypeNode
        assertThat(children.filterIsInstance<AddResourceTypeNode>()).hasSize(1)
        assertThat(children.filterIsInstance<ResourceTypeNode>()).hasSize(0)
    }

    @Test
    fun `shows resource type nodes for selected types`() {
        val mockResourceTypesManager = mock<ResourceTypesManager>()
        val mockResourceLoader = mock<ResourceLoader>()

        whenever(mockResourceTypesManager.getSelectedResourceTypes()).thenReturn(
            setOf("AWS::EC2::Instance", "AWS::S3::Bucket")
        )

        val node = ResourcesNode(projectRule.project, mockResourceTypesManager, mockResourceLoader)
        val children = node.children

        assertThat(children).hasSize(2) // Only 2 ResourceTypeNodes (no AddResourceTypeNode when types are selected)
        assertThat(children.filterIsInstance<ResourceTypeNode>()).hasSize(2)
        assertThat(children.filterIsInstance<AddResourceTypeNode>()).hasSize(0) // No AddResourceTypeNode when types are selected
    }

    @Test
    fun `resource type node shows no resources when empty`() {
        val mockResourceLoader = mock<ResourceLoader>()

        whenever(mockResourceLoader.isLoaded("AWS::EC2::Instance")).thenReturn(true)
        whenever(mockResourceLoader.getResourceIdentifiers("AWS::EC2::Instance")).thenReturn(emptyList())

        val node = ResourceTypeNode(projectRule.project, "AWS::EC2::Instance", mockResourceLoader)
        val children = node.children

        assertThat(children).hasSize(1)
        assertThat(children.first()).isInstanceOf(NoResourcesNode::class.java)
    }

    @Test
    fun `resource type node shows resource nodes when loaded`() {
        val mockResourceLoader = mock<ResourceLoader>()

        whenever(mockResourceLoader.isLoaded("AWS::EC2::Instance")).thenReturn(true)
        whenever(mockResourceLoader.getResourceIdentifiers("AWS::EC2::Instance")).thenReturn(
            listOf("testResource1", "testResource2")
        )
        whenever(mockResourceLoader.hasMore("AWS::EC2::Instance")).thenReturn(false)

        val node = ResourceTypeNode(projectRule.project, "AWS::EC2::Instance", mockResourceLoader)
        val children = node.children

        assertThat(children).hasSize(2)
        assertThat(children.filterIsInstance<ResourceNode>()).hasSize(2)
    }

    @Test
    fun `resource type node shows load more when has pagination`() {
        val mockResourceLoader = mock<ResourceLoader>()

        whenever(mockResourceLoader.isLoaded("AWS::EC2::Instance")).thenReturn(true)
        whenever(mockResourceLoader.getResourceIdentifiers("AWS::EC2::Instance")).thenReturn(
            listOf("testResource")
        )
        whenever(mockResourceLoader.hasMore("AWS::EC2::Instance")).thenReturn(true)

        val node = ResourceTypeNode(projectRule.project, "AWS::EC2::Instance", mockResourceLoader)
        val children = node.children

        assertThat(children).hasSize(2) // 1 ResourceNode + 1 LoadMoreResourcesNode
        assertThat(children.filterIsInstance<ResourceNode>()).hasSize(1)
        assertThat(children.filterIsInstance<LoadMoreResourcesNode>()).hasSize(1)
    }
}
