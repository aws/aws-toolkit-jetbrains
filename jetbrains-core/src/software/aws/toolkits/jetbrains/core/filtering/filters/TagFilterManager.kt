// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.core.filtering.filters

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.project.Project
import software.aws.toolkits.jetbrains.core.AwsResourceCache
import software.aws.toolkits.jetbrains.core.filtering.Filter
import software.aws.toolkits.jetbrains.core.filtering.FilterManager
import software.aws.toolkits.jetbrains.services.resourcegroupstaggingapi.resources.ResourceGroupsTaggingApiResources

class TagFilterManager : FilterManager {
    override val type = filterType
    override fun filter(project: Project, arn: String, serviceId: String, resourceType: String?): Boolean {
        val taggedResources = AwsResourceCache
            .getInstance(project)
            .getResourceNow(resource = ResourceGroupsTaggingApiResources.listResources(serviceId, resourceType))
        return taggedResources.filter { resource ->
            val tagMap = resource.tags().map { it.key() to it.value() }.toMap()
            resource.hasTags() && project.getState()
                .filter { it.enabled && it.tagKey.isValidTagKey() }
                .all {
                    // If there is a tag with no values, make sure the resource has the tag with any value
                    tagMap[it.tagKey] != null && (it.tagValues.isEmpty() || it.tagValues.contains(tagMap[it.tagKey]))
                }
        }.any { it.resourceARN() == arn }
    }

    override fun getFilters(project: Project): List<Filter> = project.getState()

    override fun setFilterStatus(project: Project, name: String, enabled: Boolean) {
        project.getState().first { it.name == name }.enabled = enabled
    }

    private fun Project.getState() = TagFilterStorage.getInstance(this).state
    private fun String?.isValidTagKey(): Boolean = this != null && this.isNotBlank()

    companion object {
        internal const val filterType = "tag"
    }
}

@State(name = "tagFilters", storages = [Storage("aws.xml")])
class TagFilterStorage : PersistentStateComponent<List<TagFilter>> {
    private var state: MutableList<TagFilter> = mutableListOf()
    override fun getState(): List<TagFilter> = state
    override fun loadState(s: List<TagFilter>) {
        this.state = s.toMutableList()
    }

    companion object {
        fun getInstance(project: Project): TagFilterStorage = ServiceManager.getService(project, TagFilterStorage::class.java)
    }
}

data class TagFilter(
    override val name: String = "",
    override var enabled: Boolean = true,
    val tagKey: String = "",
    val tagValues: List<String> = listOf()
) : Filter {
    override val type = TagFilterManager.filterType
}
