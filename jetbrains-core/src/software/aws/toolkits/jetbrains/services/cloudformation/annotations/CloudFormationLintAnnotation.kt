// Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0
package software.aws.toolkits.jetbrains.services.cloudformation.annotations

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.intellij.lang.annotation.HighlightSeverity

@JsonIgnoreProperties(ignoreUnknown = true)
class CloudFormationLintAnnotation {

    @JsonProperty(value = "Level")
    val level: String = ""

    @JsonProperty(value = "Location")
    val location: CloudFormationLintAnnotationLocation? = null

    @JsonProperty(value = "Rule")
    val linterRule: LinterRule? = null

    @JsonProperty(value = "Message")
    val message: String? = null

    val severity: HighlightSeverity
        get() {
            if (level.toLowerCase().contains("error")) {
                return HighlightSeverity.ERROR
            } else if (level.toLowerCase().contains("warn")) {
                return HighlightSeverity.WARNING
            }
            return HighlightSeverity.INFORMATION
        }
}
