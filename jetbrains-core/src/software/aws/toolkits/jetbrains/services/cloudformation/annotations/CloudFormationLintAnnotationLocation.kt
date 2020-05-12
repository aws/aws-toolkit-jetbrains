// Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0
package software.aws.toolkits.jetbrains.services.cloudformation.annotations

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty

data class CloudFormationLintAnnotationLocation @JsonCreator constructor(
    @JsonProperty("Start") val start: ErrorOffset,
    @JsonProperty("End") val end: ErrorOffset
) {
    class ErrorOffset {
        @JsonProperty("ColumnNumber")
        val columnNumber = 0

        @JsonProperty("LineNumber")
        val lineNumber = 0
    }
}
