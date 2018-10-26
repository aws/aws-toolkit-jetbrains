// Copyright 2018 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cloudformation

/**
 * Immutable data class for indexing [Resource]. Use [from] or [fromResource] to create an instance so that it always
 * returns a concrete [IndexedResource] such as [IndexedFunction] if applicable.
 */
open class IndexedResource protected constructor(val logicalName: String, val type: String, val indexedProperties: Map<String, String>) {

    protected constructor(resource: Resource, indexProperties: List<String>)
            : this(resource.logicalName, resource.type()!!, indexProperties.map { it to resource.getScalarProperty(it) }.toMap())

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is IndexedResource) return false

        if (logicalName != other.logicalName) return false
        if (type != other.type) return false
        if (indexedProperties != other.indexedProperties) return false

        return true
    }

    override fun hashCode(): Int {
        var result = logicalName.hashCode()
        result = 31 * result + (type.hashCode())
        result = 31 * result + indexedProperties.hashCode()
        return result
    }

    companion object {
        fun from(logicalName: String, type: String, indexedProperties: Map<String, String>) =
                INDEXED_RESOURCE_MAPPINGS[type]?.first?.invoke(logicalName, type, indexedProperties) ?: IndexedResource(logicalName, type, indexedProperties)

        fun fromResource(resource: Resource): IndexedResource? = if (resource.type() == null) null
            else INDEXED_RESOURCE_MAPPINGS[resource.type()!!]?.second?.invoke(resource) ?: IndexedResource(resource, listOf())
    }
}

class IndexedFunction(logicalName: String, type: String, indexedProperties: Map<String, String>) : IndexedResource(logicalName, type, indexedProperties) {

    internal constructor(resource: Resource) : this(resource.logicalName, resource.type()!!, listOf("Runtime", "Handler").map { it to resource.getScalarProperty(it) }.toMap())

    fun runtime() = indexedProperties["Runtime"]

    fun handler() = indexedProperties["Handler"]
}

internal val INDEXED_RESOURCE_MAPPINGS = mapOf<String, Pair<(String, String, Map<String, String>) -> IndexedResource, (Resource) -> IndexedResource>>(
        LAMBDA_FUNCTION_TYPE to Pair(::IndexedFunction, ::IndexedFunction),
        SERVERLESS_FUNCTION_TYPE to Pair(::IndexedFunction, ::IndexedFunction)
)