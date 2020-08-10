// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.core.filtering

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.project.Project

@State(name = "filters", storages = [Storage("aws.xml")])
class ResourceFilterManager : PersistentStateComponent<ResourceFilter> {
    private var state = ResourceFilter(true, mapOf())

    override fun getState(): ResourceFilter = state
    override fun loadState(state: ResourceFilter) {
        this.state = state
    }
    fun tagFilterEnabled(): Boolean = state.tagsEnabled && state.tags.any { it.value.enabled }

    companion object {
        fun getInstance(project: Project): ResourceFilterManager = ServiceManager.getService(project, ResourceFilterManager::class.java)
    }
}

data class TagFilter(
    var enabled: Boolean = false,
    var values: List<String> = listOf()
)

data class ResourceFilter(
    var tagsEnabled: Boolean = false,
    var tags: Map<String, TagFilter> = mapOf()
)
