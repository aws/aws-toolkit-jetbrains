// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.core.filtering

import com.intellij.openapi.project.Project

interface FilterManager {
    /**
     * An ID to uniquely identify the filter
     */
    val type: String

    /**
     * Determine if a resource should be filtered out.
     * @return true if the resource is not filtered by the currently applied filters or if there are
     * no filters enabled
     */
    fun filter(project: Project, arn: String, serviceId: String, resourceType: String? = null): Boolean

    /**
     * Get the filters currently associated with this resource filter type
     */
    fun getFilters(project: Project): List<Filter>

    /**
     * Set the filter status to enabled or disabled
     */
    fun setFilterStatus(project: Project, name: String, enabled: Boolean)
}
