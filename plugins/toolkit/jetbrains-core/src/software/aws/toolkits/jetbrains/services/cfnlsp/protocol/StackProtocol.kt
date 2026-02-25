// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cfnlsp.protocol

import com.google.gson.annotations.SerializedName

internal data class ListStacksParams(
    val statusToExclude: List<String>? = null,
    val loadMore: Boolean = false,
)

internal data class StackSummary(
    @SerializedName("StackName") val stackName: String? = null,
    @SerializedName("StackId") val stackId: String? = null,
    @SerializedName("StackStatus") val stackStatus: String? = null,
    @SerializedName("CreationTime") val creationTime: String? = null,
    @SerializedName("LastUpdateTime") val lastUpdatedTime: String? = null,
    @SerializedName("TemplateDescription") val templateDescription: String? = null,
)

internal data class ListStacksResult(
    val stacks: List<StackSummary>,
    val nextToken: String? = null,
)

internal data class ListChangeSetsParams(
    val stackName: String,
    val nextToken: String? = null,
)

internal data class ChangeSetInfo(
    val changeSetName: String,
    val status: String,
    val creationTime: String? = null,
    val description: String? = null,
)

internal data class ListChangeSetsResult(
    val changeSets: List<ChangeSetInfo>,
    val nextToken: String? = null,
)

internal data class DescribeStackParams(
    val stackName: String,
)

internal data class DescribeStackResult(
    val stack: StackDetail?,
)

internal data class StackDetail(
    @SerializedName("StackName")
    val stackName: String,
    @SerializedName("StackId")
    val stackId: String,
    @SerializedName("StackStatus")
    val stackStatus: String,
    @SerializedName("StackStatusReason")
    val stackStatusReason: String? = null,
    @SerializedName("CreationTime")
    val creationTime: String? = null,
    @SerializedName("LastUpdatedTime")
    val lastUpdatedTime: String? = null,
    @SerializedName("Description")
    val description: String? = null,
    @SerializedName("Outputs")
    val outputs: List<StackOutput> = emptyList(),
)

internal data class StackOutput(
    @SerializedName("OutputKey")
    val outputKey: String,
    @SerializedName("OutputValue")
    val outputValue: String,
    @SerializedName("Description")
    val description: String? = null,
)
