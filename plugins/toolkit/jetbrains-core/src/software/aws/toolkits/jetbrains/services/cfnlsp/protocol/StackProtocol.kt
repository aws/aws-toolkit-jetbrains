// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cfnlsp.protocol

internal data class ListStacksParams(
    val statusToExclude: List<String>? = null,
    val loadMore: Boolean = false,
)

// PascalCasing used to avoid serialization/gson import
internal data class StackSummary(
    val StackName: String? = null,
    val StackId: String? = null,
    val StackStatus: String? = null,
    val CreationTime: String? = null,
    val LastUpdatedTime: String? = null,
    val TemplateDescription: String? = null,
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
