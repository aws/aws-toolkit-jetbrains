// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.codemodernizer.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

const val CODETRANSFORM_METADATA_MAX_STRINGIFIED_LENGTH = 65536

@Serializable
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

    private fun trimJsonString(maxLength: Int): String {
        val jsonString = Json.encodeToString(this)

        if (jsonString.length <= maxLength) {
            return jsonString
        }

        val trimmedPropertyValues = mutableListOf<Pair<String, Any?>>()
        var currentLength = 0
        for ((key, value) in propertyValues) {
            val elementLength = key.length + value.toString().length + 5 // add 5 for quotes and comma around key-value pair
            if (currentLength + elementLength <= maxLength) {
                trimmedPropertyValues.add(Pair(key, value))
                currentLength += elementLength
            }
            // else we omit the key/value pair as a way of "trimming" the object that is too large
        }

        return Json.encodeToString(trimmedPropertyValues.toMap())
    }
}
