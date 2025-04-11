// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonq.lsp.model.adapter

import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.TypeAdapter
import com.google.gson.TypeAdapterFactory
import com.google.gson.reflect.TypeToken
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonWriter
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.Customizations
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.GetConfigurationFromServerResponse
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.Workspaces

class GetConfigurationFromServerResponseTypeAdapterFactory : TypeAdapterFactory {
    override fun <T> create(gson: Gson, type: TypeToken<T>): TypeAdapter<T>? {
        // Only handle GetConfigurationFromServerResponse
        if (GetConfigurationFromServerResponse::class.java != type.rawType) {
            return null
        }

        val delegateAdapter = gson.getDelegateAdapter(this, type)
        val elementAdapter = gson.getAdapter(JsonElement::class.java)

        @Suppress("UNCHECKED_CAST")
        return object : TypeAdapter<T>() {
            override fun write(out: JsonWriter, value: T) {
                delegateAdapter.write(out, value)
            }

            override fun read(reader: JsonReader): T? {
                val jsonElement = elementAdapter.read(reader)
                if (!jsonElement.isJsonObject) {
                    return null
                }

                val jsonObject = jsonElement.asJsonObject

                val ret =  when {
                    jsonObject.has("workspaces") -> gson.fromJson(jsonElement, Workspaces::class.java)
                    jsonObject.has("customizations") -> gson.fromJson(jsonElement, Customizations::class.java)
                    else -> delegateAdapter.fromJsonTree(jsonElement)
                } as T

                return ret
            }
        }
    }
}

