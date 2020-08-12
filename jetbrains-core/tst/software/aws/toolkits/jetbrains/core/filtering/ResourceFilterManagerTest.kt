// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.core.filtering

import com.intellij.testFramework.ProjectRule
import org.assertj.core.api.Assertions.assertThat
import org.junit.Rule
import org.junit.Test
import software.amazon.awssdk.services.resourcegroupstaggingapi.model.ResourceTagMapping
import software.amazon.awssdk.services.resourcegroupstaggingapi.model.Tag
import software.aws.toolkits.core.utils.RuleUtils
import software.aws.toolkits.jetbrains.core.MockResourceCacheRule
import software.aws.toolkits.jetbrains.services.resourcegroupstaggingapi.resources.ResourceGroupsTaggingApiResources
import java.util.concurrent.CompletableFuture

class ResourceFilterManagerTest {
    @JvmField
    @Rule
    val projectRule = ProjectRule()

    @JvmField
    @Rule
    val mockResourceCache = MockResourceCacheRule(projectRule)

    val filter = ExplorerTagFilter()
    val serviceId = RuleUtils.randomName()
    val resourceType = RuleUtils.randomName()
    val resourceArn = RuleUtils.randomName()

    @Test
    fun `tagFiltersEnabled return true if tags specified and enabled`() {
        filterManager().state["default"] = ResourceFilter(
            enabled = true,
            tags = mapOf("tag" to listOf())
        )
        assertThat(filterManager().tagFiltersEnabled()).isTrue()
    }

    @Test
    fun `tagFiltersEnabled returns false when no tags specified`() {
        filterManager().state["default"] = ResourceFilter(
            enabled = true,
            tags = mapOf()
        )
        assertThat(filterManager().tagFiltersEnabled()).isFalse()
    }

    @Test
    fun `getTaggedResources returns no resources if resource doesn't have all tags`() {
        filterManager().state["default"] = ResourceFilter(
            enabled = true,
            tags = mapOf("tag" to listOf("value"), "tag2" to listOf())
        )
        stockResourceCache(mapOf("tag" to "value"))
        assertThat(filterManager().getTaggedResources(projectRule.project, serviceId, resourceType)).isEmpty()
    }

    @Test
    fun `getTaggedResources returns no resources if resource doesn't have the correct tag value`() {
        filterManager().state["default"] = ResourceFilter(
            enabled = true,
            tags = mapOf("tag" to listOf("value"))
        )
        stockResourceCache(mapOf("tag" to "value2"))
        assertThat(filterManager().getTaggedResources(projectRule.project, serviceId, resourceType)).isEmpty()
    }

    @Test
    fun `getTaggedResources returns no resources if resource doesn't have the tag specified`() {
        filterManager().state["default"] = ResourceFilter(
            enabled = true,
            tags = mapOf("tag" to listOf("value"))
        )
        stockResourceCache(mapOf("notTag" to "value2"))
        assertThat(filterManager().getTaggedResources(projectRule.project, serviceId, resourceType)).isEmpty()
    }

    @Test
    fun `getTaggedResources returns resources if resource has matching tag and value`() {
        filterManager().state["default"] = ResourceFilter(
            enabled = true,
            tags = mapOf("tag" to listOf("value"))
        )
        stockResourceCache(mapOf("tag" to "value"))
        assertThat(filterManager().getTaggedResources(projectRule.project, serviceId, resourceType)).hasOnlyOneElementSatisfying {
            it.resourceARN() == resourceArn
        }
    }

    @Test
    fun `getTaggedResources returns resources if resource has matching tag and no values specified`() {
        filterManager().state["default"] = ResourceFilter(
            enabled = true,
            tags = mapOf("tag" to listOf())
        )
        stockResourceCache(mapOf("tag" to "value"))
        assertThat(filterManager().getTaggedResources(projectRule.project, serviceId, resourceType)).hasOnlyOneElementSatisfying {
            it.resourceARN() == resourceArn
        }
    }

    private fun filterManager() = ResourceFilterManager.getInstance(projectRule.project)
    private fun stockResourceCache(tags: Map<String, String>) {
        mockResourceCache.get().addEntry(
            ResourceGroupsTaggingApiResources.listResources(serviceId, resourceType),
            CompletableFuture.completedFuture(
                listOf(
                    ResourceTagMapping.builder().resourceARN(resourceArn).tags(tags.map { Tag.builder().key(it.key).value(it.value).build() }).build()
                )
            )
        )
    }
}
