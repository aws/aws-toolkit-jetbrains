// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonq.lsp.model

import com.fasterxml.jackson.annotation.JsonValue
import com.google.gson.Gson
import com.google.gson.TypeAdapter
import com.google.gson.TypeAdapterFactory
import com.google.gson.reflect.TypeToken

/**
 * A [Gson] [TypeAdapterFactory] that uses Jackson @[JsonValue] instead of [Enum.name] for de/serialization
 */
class EnumJsonValueAdapter : TypeAdapterFactory {
    override fun <T> create(
        gson: Gson,
        type: TypeToken<T>
    ): TypeAdapter<T>? {
        val rawType = type.getRawType()
        if (!rawType.isEnum) {
            return null
        }

        val jsonField = rawType.declaredFields.firstOrNull { it.isAnnotationPresent(JsonValue::class.java) }
            ?: return null

        jsonField.isAccessible = true

        return object : TypeAdapter<T>() {
            override fun write(out: com.google.gson.stream.JsonWriter, value: T) {
                val result = jsonField.get(value) as Any
                (gson.getAdapter(result::class.java) as TypeAdapter<Any>).write(out, result)
            }

            override fun read(`in`: com.google.gson.stream.JsonReader): T {
                val jsonValue = `in`.nextString()
                return rawType.enumConstants.first { jsonField.get(it).toString() == jsonValue } as T
            }
        } as TypeAdapter<T>
    }
}
