// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.codemodernizer.model

import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper

const val CODETRANSFORM_METADATA_MAX_STRINGIFIED_LENGTH = 65536

data class CodeTransformTelemetryMetadata(
    var dependencyVersionSelected: String? = null,
    var cancelledFromChat: Boolean = false,
) {
    private val propertyValues = listOf(
        "dependencyVersionSelected" to dependencyVersionSelected,
        "cancelledFromChat" to cancelledFromChat
    )

    operator fun iterator(): Iterator<Pair<String, Any?>> = propertyValues.iterator()

    fun toJsonString(): String {
        var trimmedJsonString = trimJsonString(CODETRANSFORM_METADATA_MAX_STRINGIFIED_LENGTH)
        return trimmedJsonString
    }

    fun resetDefaults() {
        dependencyVersionSelected = null
        cancelledFromChat = false
    }

    /**
     * @description We have a truncation function for all fields to be less than 1000 characters.
     * If this fails, we try to completely remove fields to limit the size sent to backend to prevent
     * an overflow when submitting data.
     */
    private fun trimJsonString(maxLength: Int): String {
        val objectMapper = jacksonObjectMapper()
        objectMapper.registerModule(
            SimpleModule().addSerializer(String::class.java, MaxLengthTelemetryStringSerializer())
        )
        val jsonString = objectMapper.writeValueAsString(this)
        if (jsonString.length <= maxLength) {
            return jsonString
        }

        val trimmedPropertyValues = mutableListOf<Pair<String, Any?>>()
        var currentLength = 0
        for ((key, value) in propertyValues) {
            val elementLength = key.length + value.toString().length + 5 // add 5 for quotes and comma around key-value pairs
            if (currentLength + elementLength <= maxLength) {
                trimmedPropertyValues.add(Pair(key, value))
                currentLength += elementLength
            }
            // else we omit the key/value pair as a way of "trimming" the object that is too large
        }

        return objectMapper.writeValueAsString(trimmedPropertyValues.toMap())
    }
}
