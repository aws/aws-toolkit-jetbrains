// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.core.filtering

import com.intellij.openapi.project.Project

interface Filter {
    /**
     * The type of the filter. Globally unique, matching the id of the FilterFactory used to create it
     */
    val id: String

    /**
     * Determine if a resource should be filtered out.
     * @return true if the resource matches the filter criteria
     */
    fun filter(project: Project, arn: String, serviceId: String, resourceType: String? = null): Boolean

    /**
     * Load state into a newly created instance. Throws an exception if the data in the map is invalid
     * to create this type of filter
     */
    fun loadState(state: Map<String, String>)

    /**
     * Get state serialized into a map so it can be saved back into the IDE settings.
     */
    fun getState(): Map<String, String>
}
