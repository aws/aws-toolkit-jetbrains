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
class TagFilterManager : PersistentStateComponent<ResourceFilter> {
    private var state = ResourceFilter(false, mapOf())

    override fun getState(): ResourceFilter = state
    override fun loadState(state: ResourceFilter) {
        this.state = state
    }

    fun tagFilterEnabled(): Boolean = state.tagsEnabled && state.tags.any { it.value.enabled }

    // get resources based on the currently applied filters
    fun getTaggedResources(project: Project, serviceId: String, resourceType: String? = null): List<ResourceTagMapping> {
        val taggedResources = AwsResourceCache
            .getInstance(project)
            .getResourceNow(resource = ResourceGroupsTaggingApiResources.listResources(serviceId, resourceType))
            .resourceTagMappingList()
        return taggedResources.filter { resource ->
            val tagMap = resource.tags().map { it.key() to it.value() }.toMap()
            resource.hasTags()
                && state
                .tags
                // for all enabled tags
                .filter { it.value.enabled }
                // Make sure it has one of the values that the resources tag is set to or if it's empty that it has the value at all
                .all { it.value.values.contains(tagMap[it.key]) || (it.value.values.isEmpty() && tagMap[it.key] != null) }
        }
    }

    companion object {
        fun getInstance(project: Project): TagFilterManager = ServiceManager.getService(project, TagFilterManager::class.java)
    }
}

data class ResourceTagFilter(
    var enabled: Boolean = false,
    var values: List<String> = listOf()
)

data class ResourceFilter(
    var tagsEnabled: Boolean = false,
    var tags: Map<String, ResourceTagFilter> = mapOf()
)
