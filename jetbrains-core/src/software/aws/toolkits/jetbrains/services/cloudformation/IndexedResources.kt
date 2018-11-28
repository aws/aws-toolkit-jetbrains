// Copyright 2018 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cloudformation

import software.aws.toolkits.jetbrains.services.cloudformation.IndexedResource.Companion.from
import software.aws.toolkits.resources.message
import java.io.DataInput
import java.io.DataOutput

/**
 * Immutable data class for indexing [Resource]. Use [from] to create an instance so that it always
 * returns a concrete [IndexedResource] such as [IndexedFunction] if applicable.
 */
open class IndexedResource protected constructor(val path: String, val type: String, val indexedProperties: Map<String, String>) {

    protected constructor(path: String, resource: Resource, indexProperties: List<String>)
        : this(path, resource.type() ?: throw RuntimeException(message("cloudformation.template_index.missing_type")),
            indexProperties
                .asSequence()
                .map { it to try { resource.getScalarProperty(it) } catch (e: Exception) { null } }
                .mapNotNull { (key, value) -> value?.let { key to it } }
                .toMap())

    fun save(dataOutput: DataOutput) {
        dataOutput.writeUTF(path)
        dataOutput.writeUTF(type)
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
            val propertyList: MutableMap<String, String> = mutableMapOf()

            val path = dataInput.readUTF()
            val type = dataInput.readUTF()
            val propertySize = dataInput.readInt()
            repeat(propertySize) {
                val key = dataInput.readUTF()
                val value = dataInput.readUTF()
                propertyList[key] = value
            }
            return from(path, type, propertyList)
        }

        fun from(path: String, type: String, indexedProperties: Map<String, String>) =
                INDEXED_RESOURCE_MAPPINGS[type]?.first?.invoke(path, type, indexedProperties) ?: IndexedResource(path, type, indexedProperties)

        fun from(path: String, resource: Resource): IndexedResource? = resource.type()?.let {
            INDEXED_RESOURCE_MAPPINGS[it]?.second?.invoke(path, resource) ?: IndexedResource(path, resource, listOf())
        }
    }
}

class IndexedFunction : IndexedResource {

    internal constructor(path: String, type: String, indexedProperties: Map<String, String>) : super(path, type, indexedProperties)

    internal constructor(path: String, resource: Resource) : super(path, resource, listOf("Runtime", "Handler"))

    fun runtime(): String? = indexedProperties["Runtime"]

    fun handler(): String? = indexedProperties["Handler"]

    override fun toString(): String = indexedProperties.toString()
}

internal val INDEXED_RESOURCE_MAPPINGS =
    mapOf<String, Pair<(String, String, Map<String, String>) -> IndexedResource, (String, Resource) -> IndexedResource>>(
        LAMBDA_FUNCTION_TYPE to Pair(::IndexedFunction, ::IndexedFunction),
        SERVERLESS_FUNCTION_TYPE to Pair(::IndexedFunction, ::IndexedFunction)
)