// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonqCodeTest.model

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.intellij.testIntegration.TestFramework

data class PackageInfoList(
    val member: PackageInfo? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class PackageInfo (
    val executionCommand: String? = null,
    val buildCommand: String? =null,
    val buildOrder: Int? =null,
    val testFramework: String? = null,
    val packageSummary: String? = null,
    val packagePlan: String? = null,
    val targetFileInfoList: TargetFileInfoList? = null,
)

data class TargetFileInfoList(
    val member: List<TargetFileInfo>? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class TargetFileInfo(
    val filePath: String? = null,
    val testFilePath: String? = null,
    val testCoverage: Int? = null,
    val fileSummary: String? = null,
    val filePlan: String? = null,
    val codeReferences: List<CodeReferenceInfo>? =null,
    val numberOfTestMethods: Int? = null,
)

data class CodeReferenceInfo(
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

