// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cfnlsp.protocol

import com.google.gson.annotations.SerializedName

data class ListStacksParams(
    val statusToExclude: List<String>? = null,
    val loadMore: Boolean = false,
)

data class StackSummary(
    @SerializedName("StackName")
    val stackName: String? = null,
    @SerializedName("StackId")
    val stackId: String? = null,
    @SerializedName("StackStatus")
    val stackStatus: String? = null,
    @SerializedName("CreationTime")
    val creationTime: String? = null,
    @SerializedName("LastUpdatedTime")
    val lastUpdatedTime: String? = null,
    @SerializedName("TemplateDescription")
    val templateDescription: String? = null,
)

data class ListStacksResult(
    val stacks: List<StackSummary>,
    val nextToken: String? = null,
)

data class ListChangeSetsParams(
    val stackName: String,
    val nextToken: String? = null,
)

data class ChangeSetInfo(
    val changeSetName: String,
    val status: String,
    val creationTime: String? = null,
    val description: String? = null,
)

data class ListChangeSetsResult(
    val changeSets: List<ChangeSetInfo>,
    val nextToken: String? = null,
)
