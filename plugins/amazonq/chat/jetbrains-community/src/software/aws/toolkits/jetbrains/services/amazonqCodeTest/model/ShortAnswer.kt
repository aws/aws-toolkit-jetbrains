// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonqCodeTest.model

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

data class ShortAnswerReference(
    val licenseName: String? = null,
    val repository: String? = null,
    val url: String? = null,
    val recommendationContentSpan: RecommendationContentSpan? = null,
) {
    data class RecommendationContentSpan(
        val start: Int,
        val end: Int,
    )
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class ShortAnswer(
    val sourceFilePath: String? = null,

    val testFramework: String? = null,

    val testFilePath: String? = null,

    val buildCommand: String? = null,

    val executionCommand: String? = null,

    val testCoverage: String? = null,

    val stopIteration: String? = null,

    val planSummary: String? = null,

    val codeReferences: List<ShortAnswerReference>? = null,

    val numberOfTestMethods: Int? = 0,
)
