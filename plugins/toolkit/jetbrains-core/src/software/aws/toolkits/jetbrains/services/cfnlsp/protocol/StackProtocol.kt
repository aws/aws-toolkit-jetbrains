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
    val outputs: List<StackOutput>? = null,
    @SerializedName("Parameters")
    val parameters: List<Parameter>? = null,
    @SerializedName("Tags")
    val tags: List<Tag>? = null,
)

internal data class StackOutput(
    @SerializedName("OutputKey")
    val outputKey: String,
    @SerializedName("OutputValue")
    val outputValue: String,
    @SerializedName("Description")
    val description: String? = null,
    @SerializedName("ExportName")
    val exportName: String? = null,
)

// Stack Resources Protocol
internal data class GetStackResourcesParams(
    val stackName: String,
    val nextToken: String? = null,
)

internal data class StackResourceSummary(
    @SerializedName("LogicalResourceId")
    val logicalResourceId: String,
    @SerializedName("PhysicalResourceId")
    val physicalResourceId: String?,
    @SerializedName("ResourceType")
    val resourceType: String,
    @SerializedName("ResourceStatus")
    val resourceStatus: String,
    @SerializedName("Timestamp")
    val timestamp: String?,
)

internal data class ListStackResourcesResult(
    val resources: List<StackResourceSummary>,
    val nextToken: String? = null,
)

// Stack Events Protocol
internal data class GetStackEventsParams(
    val stackName: String,
    val nextToken: String? = null,
    val refresh: Boolean? = null,
)

internal data class GetStackEventsResult(
    val events: List<StackEvent>,
    val nextToken: String? = null,
    val gapDetected: Boolean? = null,
)

internal data class StackEvent(
    @SerializedName("StackId")
    val stackId: String? = null,
    @SerializedName("EventId")
    val eventId: String? = null,
    @SerializedName("StackName")
    val stackName: String? = null,
    @SerializedName("LogicalResourceId")
    val logicalResourceId: String? = null,
    @SerializedName("PhysicalResourceId")
    val physicalResourceId: String? = null,
    @SerializedName("ResourceType")
    val resourceType: String? = null,
    @SerializedName("Timestamp")
    val timestamp: String? = null,
    @SerializedName("ResourceStatus")
    val resourceStatus: String? = null,
    @SerializedName("ResourceStatusReason")
    val resourceStatusReason: String? = null,
    @SerializedName("ResourceProperties")
    val resourceProperties: String? = null,
    @SerializedName("ClientRequestToken")
    val clientRequestToken: String? = null,
    @SerializedName("OperationId")
    val operationId: String? = null,
    @SerializedName("HookType")
    val hookType: String? = null,
    @SerializedName("HookStatus")
    val hookStatus: String? = null,
    @SerializedName("HookStatusReason")
    val hookStatusReason: String? = null,
    @SerializedName("HookInvocationPoint")
    val hookInvocationPoint: String? = null,
    @SerializedName("HookFailureMode")
    val hookFailureMode: String? = null,
)

internal data class ClearStackEventsParams(
    val stackName: String,
)
