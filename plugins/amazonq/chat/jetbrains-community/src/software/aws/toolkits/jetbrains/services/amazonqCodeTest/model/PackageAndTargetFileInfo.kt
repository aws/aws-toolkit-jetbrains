// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonqCodeTest.model

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

data class PackageInfoList(
    var member: PackageInfo? = null,
) : MutableList<software.amazon.awssdk.services.codewhispererruntime.model.PackageInfo> {
    override val size: Int
        get() = TODO("Not yet implemented")

    override fun add(element: software.amazon.awssdk.services.codewhispererruntime.model.PackageInfo): Boolean {
        TODO("Not yet implemented")
    }

    override fun add(index: Int, element: software.amazon.awssdk.services.codewhispererruntime.model.PackageInfo) {
        TODO("Not yet implemented")
    }

    override fun addAll(index: Int, elements: Collection<software.amazon.awssdk.services.codewhispererruntime.model.PackageInfo>): Boolean {
        TODO("Not yet implemented")
    }

    override fun addAll(elements: Collection<software.amazon.awssdk.services.codewhispererruntime.model.PackageInfo>): Boolean {
        TODO("Not yet implemented")
    }

    override fun clear() {
        TODO("Not yet implemented")
    }

    override fun contains(element: software.amazon.awssdk.services.codewhispererruntime.model.PackageInfo): Boolean {
        TODO("Not yet implemented")
    }

    override fun containsAll(elements: Collection<software.amazon.awssdk.services.codewhispererruntime.model.PackageInfo>): Boolean {
        TODO("Not yet implemented")
    }

    override fun get(index: Int): software.amazon.awssdk.services.codewhispererruntime.model.PackageInfo {
        TODO("Not yet implemented")
    }

    override fun indexOf(element: software.amazon.awssdk.services.codewhispererruntime.model.PackageInfo): Int {
        TODO("Not yet implemented")
    }

    override fun isEmpty(): Boolean {
        TODO("Not yet implemented")
    }

    override fun iterator(): MutableIterator<software.amazon.awssdk.services.codewhispererruntime.model.PackageInfo> {
        TODO("Not yet implemented")
    }

    override fun lastIndexOf(element: software.amazon.awssdk.services.codewhispererruntime.model.PackageInfo): Int {
        TODO("Not yet implemented")
    }

    override fun listIterator(): MutableListIterator<software.amazon.awssdk.services.codewhispererruntime.model.PackageInfo> {
        TODO("Not yet implemented")
    }

    override fun listIterator(index: Int): MutableListIterator<software.amazon.awssdk.services.codewhispererruntime.model.PackageInfo> {
        TODO("Not yet implemented")
    }

    override fun remove(element: software.amazon.awssdk.services.codewhispererruntime.model.PackageInfo): Boolean {
        TODO("Not yet implemented")
    }

    override fun removeAll(elements: Collection<software.amazon.awssdk.services.codewhispererruntime.model.PackageInfo>): Boolean {
        TODO("Not yet implemented")
    }

    override fun removeAt(index: Int): software.amazon.awssdk.services.codewhispererruntime.model.PackageInfo {
        TODO("Not yet implemented")
    }

    override fun retainAll(elements: Collection<software.amazon.awssdk.services.codewhispererruntime.model.PackageInfo>): Boolean {
        TODO("Not yet implemented")
    }

    override fun set(
        index: Int,
        element: software.amazon.awssdk.services.codewhispererruntime.model.PackageInfo,
    ): software.amazon.awssdk.services.codewhispererruntime.model.PackageInfo {
        TODO("Not yet implemented")
    }

    override fun subList(fromIndex: Int, toIndex: Int): MutableList<software.amazon.awssdk.services.codewhispererruntime.model.PackageInfo> {
        TODO("Not yet implemented")
    }
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class PackageInfo(
    val executionCommand: String? = null,
    val buildCommand: String? = null,
    val buildOrder: Int? = null,
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
    val codeReferences: List<CodeReferenceInfo>? = null,
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
