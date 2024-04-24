// Copyright 2023 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.codemodernizer.model

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

@JsonIgnoreProperties(ignoreUnknown = true)
data class PlanTableRow(
    @JsonProperty("Name")
    val name: String?,
    @JsonProperty("Value")
    val value: String?,
    @JsonProperty("Dependency")
    val dependency: String?,
    @JsonProperty("Action")
    val action: String?,
    @JsonProperty("Current version")
    val currentVersion: String?,
    @JsonProperty("Target version")
    val targetVersion: String?,
    @JsonProperty("Deprecated code")
    val deprecatedCode: String?,
    @JsonProperty("Suggested replacement")
    val suggestedReplacement: String?,
    @JsonProperty("Files to be changed")
    val filesToBeChanged: String?,
    @JsonProperty("File name")
    val fileName: String?
) {
    fun getValueForColumn(col: String): String? {
        // do not need "name" and "value" here since they are not used to display our tables
        return when (col) {
            "Dependency" -> dependency
            "Action" -> action
            "Current version" -> currentVersion
            "Target version" -> targetVersion
            "Deprecated code" -> deprecatedCode
            "Suggested replacement" -> suggestedReplacement
            "Files to be changed" -> filesToBeChanged
            "File name" -> fileName
            else -> "-"
        }
    }
}
