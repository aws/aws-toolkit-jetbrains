// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.core.filtering

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.project.Project
import com.intellij.util.xmlb.Converter
import com.intellij.util.xmlb.annotations.Tag
import com.intellij.util.xmlb.annotations.XMap

@State(name = "filters", storages = [Storage("aws.xml")])
class ResourceFilterManager : PersistentStateComponent<ResourceFilter> {
    private var state = ResourceFilter(true, mutableMapOf())

    override fun getState(): ResourceFilter = state
    override fun loadState(state: ResourceFilter) {
        this.state = state
    }

    fun tagFilterEnabled(): Boolean = state.tagsEnabled && state.tags.any { it.value.enabled }

    companion object {
        fun getInstance(project: Project): ResourceFilterManager = ServiceManager.getService(project, ResourceFilterManager::class.java)
    }
}

class TagFilterConverter : Converter<TagFilter>() {
    override fun toString(value: TagFilter): String = "${value.enabled},${value.values}"
    override fun fromString(value: String): TagFilter? {
        return try {
            val values = value.split(",")
            TagFilter(
                values[0].toBoolean(),
                values.subList(1, values.size)
            )
        } catch (e: Exception) {
            null
        }
    }
}

data class TagFilter(
    var enabled: Boolean = false,
    var values: List<String> = listOf()
)

data class ResourceFilter(
    var tagsEnabled: Boolean = false,
    var tags: Map<String, TagFilter> = mapOf()
    // val stacks: MutableList<Pair<Boolean, String>>,
)
