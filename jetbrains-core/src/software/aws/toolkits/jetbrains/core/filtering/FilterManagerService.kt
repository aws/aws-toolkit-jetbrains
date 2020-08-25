// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.core.filtering

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import kotlin.streams.toList

data class SerializedFilter(
    var enabled: Boolean = false,
    var id: String = "",
    var data: Map<String, String> = mapOf()
)

data class FilterManagerState(
    var state: MutableMap<String, SerializedFilter> = mutableMapOf()
)

@State(name = "resourceFilters", storages = [Storage("aws.xml")])
class FilterManagerService(private val project: Project) : PersistentStateComponent<FilterManagerState> {
    private var state: MutableMap<String, Pair<Boolean, Filter>> = mutableMapOf()

    override fun getState(): FilterManagerState = FilterManagerState(state.map { (key, value) ->
        key to SerializedFilter(value.first, value.second.id, value.second.getState())
    }.toMap().toMutableMap())

    override fun loadState(newState: FilterManagerState) {
        state.clear()
        newState.state.forEach { (key, value) ->
            val factory = getFilterFactory(value.id) ?: return@forEach
            val filter = factory.createFilter(value.data) ?: return@forEach
            state[key] = Pair(value.enabled, filter)
        }
    }

    /**
     * Determine if the resource should be filtered out. ANDs together all currently
     * enabled filters.
     * @return true if the resource matches ALL of the currently applied filters
     */
    fun filter(arn: String, serviceId: String, resourceType: String? = null): Boolean =
        getEnabledFilters().all { (_, value) -> value.second.filter(project, arn, serviceId, resourceType) }

    /**
     * Determine if any filters are enabled
     */
    fun filtersEnabled() = getEnabledFilters().isNotEmpty()

    private fun getEnabledFilters() = state.filter { (_, value) -> value.first }
    private fun getFilterFactory(id: String): FilterFactory? = FACTORY_EP_NAME.extensions().toList().firstOrNull { it.id == id }

    companion object {
        fun getInstance(project: Project): FilterManagerService = ServiceManager.getService(project, FilterManagerService::class.java)
        private val FACTORY_EP_NAME = ExtensionPointName.create<FilterFactory>("aws.toolkit.resourceFilter")
    }
}
