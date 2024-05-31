// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0
package software.aws.toolkits.jetbrains.services.codemodernizer.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

const val MAX_STRINGIFIED_LENGTH = 65536

@Serializable
data class CodeTransformTelemetryMetadata(
    val dependencyVersionSelected: String? = null,
    val cancelledFromChat: Boolean = false,
) {

    fun toJsonString(): String {
        var trimmedJsonString = trimJsonString(MAX_STRINGIFIED_LENGTH)
        return trimmedJsonString
    }

    private fun trimJsonString(maxLength: Int): String {
        val jsonString = Json.encodeToString(this)

        if (jsonString.length <= maxLength) {
            return jsonString
        }

        val jsonElements = jsonString.split(",")
        val trimmedJsonElements = mutableListOf<String>()
        var currentLength = 0

        for (element in jsonElements) {
            val elementLength = element.length + 1 // Add 1 for the comma
            if (currentLength + elementLength <= maxLength) {
                trimmedJsonElements.add(element)
                currentLength += elementLength
            } else {
                break
            }
        }

        val trimmedJsonString = trimmedJsonElements.joinToString(",", "{", "}")
        return if (trimmedJsonString.length <= maxLength) {
            trimmedJsonString
        } else {
            trimmedJsonString.substring(0, maxLength)
        }
    }
}
