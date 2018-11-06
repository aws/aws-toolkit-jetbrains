// Copyright 2018 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cloudformation

import java.io.DataInput
import java.io.DataOutput

/**
 * Immutable data class for indexing [Resource]. Use [from] to create an instance so that it always
 * returns a concrete [IndexedResource] such as [IndexedFunction] if applicable.
 */
open class IndexedResource protected constructor(val indexedProperties: Map<String, String>) {

    protected constructor(resource: Resource, indexProperties: List<String>)
        : this(indexProperties
            .asSequence()
            .map { it to try { resource.getScalarProperty(it) } catch (e: Exception) { null } }
            .mapNotNull { (key, value) -> value?.let { key to it } }
            .toMap())

    fun save(dataOutput: DataOutput) {
        dataOutput.writeInt(indexedProperties.size)
        indexedProperties.forEach { key, value ->
            dataOutput.writeUTF(key)
            dataOutput.writeUTF(value)
        }
    }

    override fun equals(other: Any?): Boolean = this === other || (other as? IndexedResource)?.indexedProperties == indexedProperties

    override fun hashCode(): Int = indexedProperties.hashCode()

    companion object {
        fun read(dataInput: DataInput): IndexedResource {
            val mutableMap: MutableMap<String, String> = mutableMapOf()

            val propertySize = dataInput.readInt()
            repeat(propertySize) {
                val key = dataInput.readUTF()
                val value = dataInput.readUTF()
                mutableMap[key] = value
            }
            return IndexedResource(mutableMap)
        }

        fun from(type: String, indexedProperties: Map<String, String>) =
                INDEXED_RESOURCE_MAPPINGS[type]?.first?.invoke(indexedProperties) ?: IndexedResource(indexedProperties)

        fun from(resource: Resource): IndexedResource? =
                INDEXED_RESOURCE_MAPPINGS[resource.type()]?.second?.invoke(resource) ?: IndexedResource(resource, listOf())
    }
}

class IndexedFunction : IndexedResource {

    internal constructor(indexedProperties: Map<String, String>) : super(indexedProperties)

    internal constructor(resource: Resource) : super(resource, listOf("Runtime", "Handler"))

    fun runtime(): String? = indexedProperties["Runtime"]

    fun handler(): String? = indexedProperties["Handler"]
}

internal val INDEXED_RESOURCE_MAPPINGS = mapOf<String, Pair<(Map<String, String>) -> IndexedResource, (Resource) -> IndexedResource>>(
        LAMBDA_FUNCTION_TYPE to Pair(::IndexedFunction, ::IndexedFunction),
        SERVERLESS_FUNCTION_TYPE to Pair(::IndexedFunction, ::IndexedFunction)
)