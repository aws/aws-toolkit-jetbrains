// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.core.filtering

import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project

class FilterManagerService(private val project: Project) {
    /**
     * Determine if the resource should be filtered out.
     * @return true if the resource is not filtered by the currently applied filters
     */
    fun filter(arn: String, serviceId: String, resourceType: String? = null): Boolean =
        getManagers().all { it.filter(project, arn, serviceId, resourceType) }

    fun filtersEnabled() = getManagers().any { it.getFilters(project).any { filter -> filter.enabled } }
    fun getAllFilters() = getManagers().flatMap { it.getFilters(project) }

    private fun getManagers(): List<FilterManager> = EP_NAME.extensionList

    companion object {
        fun getInstance(project: Project): FilterManagerService = ServiceManager.getService(project, FilterManagerService::class.java)
        val EP_NAME = ExtensionPointName.create<FilterManager>("aws.toolkit.resourceFilter")
    }
}
