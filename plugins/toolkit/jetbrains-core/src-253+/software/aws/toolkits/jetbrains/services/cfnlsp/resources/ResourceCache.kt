// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cfnlsp.resources

import java.util.concurrent.ConcurrentHashMap

internal data class ResourceTypeData(
    val resourceIdentifiers: List<String>,
    val nextToken: String? = null,
    val loaded: Boolean = false,
)

internal class ResourceCache {
    private val resourcesByType = ConcurrentHashMap<String, ResourceTypeData>()

    fun get(resourceType: String): ResourceTypeData? = resourcesByType[resourceType]

    fun put(resourceType: String, data: ResourceTypeData) {
        resourcesByType[resourceType] = data
    }

    fun remove(resourceType: String) = resourcesByType.remove(resourceType)

    fun keys(): Set<String> = resourcesByType.keys.toSet()

    fun clear() = resourcesByType.clear()
}
