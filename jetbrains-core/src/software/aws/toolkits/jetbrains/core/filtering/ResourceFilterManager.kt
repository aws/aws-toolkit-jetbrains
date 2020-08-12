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

    fun tagFiltersEnabled(): Boolean = state.any { it.value.enabled && it.value.tags.isNotEmpty() }

    // get resources based on the currently applied filters
    fun getTaggedResources(project: Project, serviceId: String, resourceType: String? = null): List<ResourceTagMapping> {
        val taggedResources = AwsResourceCache
            .getInstance(project)
            .getResourceNow(resource = ResourceGroupsTaggingApiResources.listResources(serviceId, resourceType))
        return taggedResources.filter { resource ->
            val tagMap = resource.tags().map { it.key() to it.value() }.toMap()
            resource.hasTags() && state
                // Only show enabled filters with tags
                .filter { it.value.enabled && it.value.tags.isNotEmpty() }
                // convert the list of key values to just a list of key values
                .flatMap { it.value.tags.toList() }
                .all { (key, values) ->
                    // If there is a tag with no values, make sure the resource has the tag with any value
                    tagMap[key] != null && (values.isEmpty() || values.contains(tagMap[key]))
                }
        }
    }

    companion object {
        fun getInstance(project: Project): ResourceFilterManager = ServiceManager.getService(project, ResourceFilterManager::class.java)
    }
}

data class ResourceFilter(
    var enabled: Boolean = true,
    var tags: Map<String, List<String>> = mapOf(),
    var stacks: List<String> = listOf()
)

typealias ResourceFilters = MutableMap<String, ResourceFilter>
