// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.core.filtering

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.project.Project

@State(name = "filter", storages = [Storage("aws.xml")])
class ResourceFilterManager : PersistentStateComponent<ResourceFilter> {
    private var state = ResourceFilter(false, mutableListOf(), mutableMapOf())
    /*
    val filters = mutableMapOf<String, MutableList<String>>(
        "SoftwareType" to mutableListOf("Infrastructure", "Long-Running Server-Side Software")
    )*/

    override fun getState(): ResourceFilter = state
    override fun loadState(state: ResourceFilter) {
        this.state = state
    }

    companion object {
        fun getInstance(project: Project): ResourceFilterManager = ServiceManager.getService(project, ResourceFilterManager::class.java)
    }
}

data class ResourceFilter(
    var enabled: Boolean,
    val stacks: MutableList<Pair<Boolean, String>>,
    val tags: MutableMap<String, Pair<Boolean, MutableList<String>>>
)
