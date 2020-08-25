// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.core.filtering

interface FilterFactory {
    /**
     * The ID of the Filter Factory. Globally unique, and used to determine which
     * FilterFactory instance to grab.
     */
    val id: String

    /**
     * Create a filter (of the filter factory type) using a map of values.
     * @return The created filter on success, or null on failure. Factories
     * are responsible for logging these failures in a reasonable way
     */
    fun createFilter(state: Map<String, String>): Filter?
}
