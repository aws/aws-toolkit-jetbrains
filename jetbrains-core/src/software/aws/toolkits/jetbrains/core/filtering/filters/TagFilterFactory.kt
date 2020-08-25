// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.core.filtering.filters

import com.intellij.openapi.project.Project
import software.aws.toolkits.core.utils.getLogger
import software.aws.toolkits.jetbrains.core.AwsResourceCache
import software.aws.toolkits.jetbrains.core.filtering.Filter
import software.aws.toolkits.jetbrains.core.filtering.FilterFactory
import software.aws.toolkits.jetbrains.services.resourcegroupstaggingapi.resources.ResourceGroupsTaggingApiResources

class TagFilter : Filter {
    override val id = filterType

    private var tagKey = "SoftwareType"
    private var tagValues = listOf<String>()

    override fun filter(project: Project, arn: String, serviceId: String, resourceType: String?): Boolean {
        val taggedResources = AwsResourceCache
            .getInstance(project)
            .getResourceNow(resource = ResourceGroupsTaggingApiResources.listResources(serviceId, resourceType))
        return taggedResources.filter { resource ->
            val tagMap = resource.tags().map { it.key() to it.value() }.toMap()
            resource.hasTags() && tagMap[tagKey] != null && (tagValues.isEmpty() || tagValues.contains(tagMap[tagKey]))
        }.any { it.resourceARN() == arn }
    }

    override fun loadState(state: Map<String, String>) {
        if (!state[tagKeyKey].isValidTagKey()) {
            throw IllegalStateException("Invalid tag filter passed into loadState! state:\n$state")
        }
        state[tagKeyKey]?.let { tagKey = it }
        state[tagValueKey]?.let { tagValues = it.split(",").map { str -> str.trim() } }
    }

    override fun getState(): Map<String, String> = mapOf(
        tagKeyKey to tagKey,
        tagValueKey to tagValues.joinToString(",")
    )

    private fun String?.isValidTagKey(): Boolean = this != null && this.isNotBlank()

    companion object {
        const val tagKeyKey = "TAGKEY"
        const val tagValueKey = "TAGVALUES"
        const val filterType = "tag"
    }
}

class TagFilterFactory : FilterFactory {
    override val id = TagFilter.filterType
    override fun createFilter(state: Map<String, String>): Filter? = try {
        val filter = TagFilter()
        filter.loadState(state)
        filter
    } catch (e: Exception) {
        LOG.warn("Unable to create Tag filter with state $state", e)
        null
    }

    private companion object {
        val LOG = getLogger<TagFilterFactory>()
    }
}
