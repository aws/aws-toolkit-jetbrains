// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.core.filtering

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.project.Project
import software.amazon.awssdk.services.resourcegroupstaggingapi.model.ResourceTagMapping
import software.aws.toolkits.jetbrains.core.AwsResourceCache
import software.aws.toolkits.jetbrains.services.resourcegroupstaggingapi.resources.ResourceGroupsTaggingApiResources

@State(name = "filters", storages = [Storage("aws.xml")])
class ResourceFilterManager : PersistentStateComponent<ResourceFilters> {
    private var state: ResourceFilters = mutableMapOf()

    override fun getState(): ResourceFilters = state
    override fun loadState(state: ResourceFilters) {
        this.state = state
    }

    fun tagFiltersEnabled(): Boolean = state.values.filterIsInstance<TagFilter>().any { it.enabled && it.tagKey.isValidTagKey() }

    // get resources based on the currently applied filters
    fun getTaggedResources(project: Project, serviceId: String, resourceType: String? = null): List<ResourceTagMapping> {
        val taggedResources = AwsResourceCache
            .getInstance(project)
            .getResourceNow(resource = ResourceGroupsTaggingApiResources.listResources(serviceId, resourceType))
        return taggedResources.filter { resource ->
            val tagMap = resource.tags().map { it.key() to it.value() }.toMap()
            resource.hasTags() && state
                .values
                .filterIsInstance<TagFilter>()
                // Only show enabled filters with tags
                .filter { it.enabled && it.tagKey.isValidTagKey() }
                .all {
                    // If there is a tag with no values, make sure the resource has the tag with any value
                    tagMap[it.tagKey] != null && (it.tagValues.isEmpty() || it.tagValues.contains(tagMap[it.tagKey]))
                }
        }
    }

    companion object {
        fun getInstance(project: Project): ResourceFilterManager = ServiceManager.getService(project, ResourceFilterManager::class.java)
    }
}

sealed class ResourceFilter(open val enabled: Boolean)
data class TagFilter(
    override val enabled: Boolean = true,
    var tagKey: String = "",
    var tagValues: List<String> = listOf()
) : ResourceFilter(enabled)

data class StackFilter(
    override val enabled: Boolean = true,
    val stackID: String = ""
) : ResourceFilter(enabled)

typealias ResourceFilters = MutableMap<String, ResourceFilter>
