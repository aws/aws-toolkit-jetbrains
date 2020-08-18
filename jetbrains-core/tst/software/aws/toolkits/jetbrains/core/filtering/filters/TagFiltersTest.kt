// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.core.filtering.filters

import com.intellij.testFramework.ProjectRule
import org.junit.Rule
import software.aws.toolkits.core.utils.RuleUtils
import software.aws.toolkits.jetbrains.core.MockResourceCacheRule

class TagFiltersTest {
    @JvmField
    @Rule
    val projectRule = ProjectRule()

    @JvmField
    @Rule
    val mockResourceCache = MockResourceCacheRule(projectRule)

    private val serviceId = RuleUtils.randomName()
    private val resourceType = RuleUtils.randomName()
    private val resourceArn = RuleUtils.randomName()
/*
    @Before
    fun clear() {
        filterManager().state.clear()
    }

    @Test
    fun `tagFiltersEnabled returns false when only invalid tags specified`() {
        filterManager().state["default"] = TagFilter(
            enabled = true,
            tagKey = " ",
            tagValues = listOf()
        )
        assertThat(filterManager().tagFiltersEnabled()).isFalse()
    }

    @Test
    fun `tagFiltersEnabled returns false when all tags are disabled`() {
        filterManager().state["default"] = TagFilter(
            enabled = false,
            tagKey = "key",
            tagValues = listOf()
        )
        assertThat(filterManager().tagFiltersEnabled()).isFalse()
    }

    @Test
    fun `getTaggedResources returns no resources if resource doesn't have all tags`() {
        filterManager().state["default"] = TagFilter(
            enabled = true,
            tagKey = "tag",
            tagValues = listOf("value")
        )
        filterManager().state["default2"] = TagFilter(
            enabled = true,
            tagKey = "tag2",
            tagValues = listOf()
        )
        stockResourceCache(mapOf("tag" to "value"))
        assertThat(filterManager().filter(projectRule.project, serviceId, resourceType)).isEmpty()
    }

    @Test
    fun `getTaggedResources returns no resources if resource doesn't have the correct tag value`() {
        filterManager().state["default"] = TagFilter(
            enabled = true,
            tagKey = "tag",
            tagValues = listOf("value")
        )
        stockResourceCache(mapOf("tag" to "value2"))
        assertThat(filterManager().filter(projectRule.project, serviceId, resourceType)).isEmpty()
    }

    @Test
    fun `getTaggedResources returns no resources if resource doesn't have the tag specified`() {
        filterManager().state["default"] = TagFilter(
            enabled = true,
            tagKey = "tag",
            tagValues = listOf("value")
        )
        stockResourceCache(mapOf("notTag" to "value2"))
        assertThat(filterManager().filter(projectRule.project, serviceId, resourceType)).isEmpty()
    }

    @Test
    fun `getTaggedResources filters out untagged resources`() {
        filterManager().state["default"] = TagFilter(
            enabled = true,
            tagKey = "tag",
            tagValues = listOf("value")
        )
        stockResourceCache(mapOf())
        assertThat(filterManager().filter(projectRule.project, serviceId, resourceType)).isEmpty()
    }

    @Test
    fun `getTaggedResources returns resources if resource has matching tags and value`() {
        filterManager().state["default"] = TagFilter(
            enabled = true,
            tagKey = "tag",
            tagValues = listOf("value")
        )
        filterManager().state["default2"] = TagFilter(
            enabled = true,
            tagKey = "tag2",
            tagValues = listOf("Value2")
        )
        stockResourceCache(mapOf("tag" to "value", "tag2" to "Value2"))
        assertThat(filterManager().filter(projectRule.project, serviceId, resourceType)).hasOnlyOneElementSatisfying {
            it.resourceARN() == resourceArn
        }
    }

    @Test
    fun `getTaggedResources returns resources if resource has matching tag and no values specified`() {
        filterManager().state["default"] = TagFilter(
            enabled = true,
            tagKey = "tag",
            tagValues = listOf()
        )
        stockResourceCache(mapOf("tag" to "value"))
        assertThat(filterManager().filter(projectRule.project, serviceId, resourceType)).hasOnlyOneElementSatisfying {
            it.resourceARN() == resourceArn
        }
    }

    private fun filterManager() = TagFilter()
    private fun stockResourceCache(tags: Map<String, String>) {
        mockResourceCache.get().addEntry(
            ResourceGroupsTaggingApiResources.listResources(serviceId, resourceType),
            CompletableFuture.completedFuture(
                listOf(
                    ResourceTagMapping.builder().resourceARN(resourceArn).tags(tags.map { Tag.builder().key(it.key).value(it.value).build() }).build()
                )
            )
        )
    }*/
}
