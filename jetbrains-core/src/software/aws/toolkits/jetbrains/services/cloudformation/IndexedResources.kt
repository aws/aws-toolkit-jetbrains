// Copyright 2018 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cloudformation

/**
 * Immutable data class for indexing [Resource]. Use [from] to create an instance so that it always
 * returns a concrete [IndexedResource] such as [IndexedFunction] if applicable.
 */
open class IndexedResource internal constructor(val indexedProperties: Map<String, String>) {

    protected constructor(resource: Resource, indexProperties: List<String>)
            : this(indexProperties.map {
        it to try {
            resource.getScalarProperty(it)
        } catch (e: Exception) {
            ""
        }
    }.toMap())

    override fun equals(other: Any?): Boolean = this === other || (other as? IndexedResource)?.indexedProperties == indexedProperties

    override fun hashCode(): Int = indexedProperties.hashCode()

    companion object {
        fun from(type: String, indexedProperties: Map<String, String>) =
                INDEXED_RESOURCE_MAPPINGS[type]?.first?.invoke(indexedProperties) ?: IndexedResource(indexedProperties)

        fun from(resource: Resource): IndexedResource? =
                INDEXED_RESOURCE_MAPPINGS[resource.type()]?.second?.invoke(resource) ?: IndexedResource(resource, listOf())
    }
}

class IndexedFunction : IndexedResource {

    internal constructor(indexedProperties: Map<String, String>) : super(indexedProperties)

    internal constructor(resource: Resource) : super(resource, listOf("Runtime", "Handler"))

    fun runtime() = indexedProperties["Runtime"]

    fun handler() = indexedProperties["Handler"]
}

internal val INDEXED_RESOURCE_MAPPINGS = mapOf<String, Pair<(Map<String, String>) -> IndexedResource, (Resource) -> IndexedResource>>(
        LAMBDA_FUNCTION_TYPE to Pair(::IndexedFunction, ::IndexedFunction),
        SERVERLESS_FUNCTION_TYPE to Pair(::IndexedFunction, ::IndexedFunction)
)