// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cfnlsp

import org.eclipse.lsp4j.jsonrpc.services.JsonRequest
import org.eclipse.lsp4j.services.LanguageServer
import software.aws.toolkits.jetbrains.services.cfnlsp.protocol.CreateStackActionResult
import software.aws.toolkits.jetbrains.services.cfnlsp.protocol.CreateValidationParams
import software.aws.toolkits.jetbrains.services.cfnlsp.protocol.DescribeStackParams
import software.aws.toolkits.jetbrains.services.cfnlsp.protocol.DescribeStackResult
import software.aws.toolkits.jetbrains.services.cfnlsp.protocol.DescribeValidationStatusResult
import software.aws.toolkits.jetbrains.services.cfnlsp.protocol.GetStackActionStatusResult
import software.aws.toolkits.jetbrains.services.cfnlsp.protocol.Identifiable
import software.aws.toolkits.jetbrains.services.cfnlsp.protocol.ListChangeSetsParams
import software.aws.toolkits.jetbrains.services.cfnlsp.protocol.ListChangeSetsResult
import software.aws.toolkits.jetbrains.services.cfnlsp.protocol.ListResourcesParams
import software.aws.toolkits.jetbrains.services.cfnlsp.protocol.ListResourcesResult
import software.aws.toolkits.jetbrains.services.cfnlsp.protocol.ListStacksParams
import software.aws.toolkits.jetbrains.services.cfnlsp.protocol.ListStacksResult
import software.aws.toolkits.jetbrains.services.cfnlsp.protocol.RefreshResourcesParams
import software.aws.toolkits.jetbrains.services.cfnlsp.protocol.RefreshResourcesResult
import software.aws.toolkits.jetbrains.services.cfnlsp.protocol.ResourceStackManagementResult
import software.aws.toolkits.jetbrains.services.cfnlsp.protocol.ResourceStateParams
import software.aws.toolkits.jetbrains.services.cfnlsp.protocol.ResourceStateResult
import software.aws.toolkits.jetbrains.services.cfnlsp.protocol.ResourceTypesResult
import software.aws.toolkits.jetbrains.services.cfnlsp.protocol.SearchResourceParams
import software.aws.toolkits.jetbrains.services.cfnlsp.protocol.SearchResourceResult
import software.aws.toolkits.jetbrains.services.cfnlsp.protocol.UpdateCredentialsParams
import software.aws.toolkits.jetbrains.services.cfnlsp.protocol.UpdateCredentialsResult
import java.util.concurrent.CompletableFuture

/**
 * Extended LSP server interface for CloudFormation Language Server.
 * Defines custom protocol methods beyond standard LSP.
 */
internal interface CfnLspServerProtocol : LanguageServer {
    @JsonRequest("aws/credentials/iam/update")
    fun updateIamCredentials(params: UpdateCredentialsParams): CompletableFuture<UpdateCredentialsResult>

    @JsonRequest("aws/cfn/stacks")
    fun listStacks(params: ListStacksParams): CompletableFuture<ListStacksResult>

    @JsonRequest("aws/cfn/stack/changeSet/list")
    fun listChangeSets(params: ListChangeSetsParams): CompletableFuture<ListChangeSetsResult>

    @JsonRequest("aws/cfn/resources/types")
    fun listResourceTypes(): CompletableFuture<ResourceTypesResult>

    @JsonRequest("aws/cfn/resources/list")
    fun listResources(params: ListResourcesParams): CompletableFuture<ListResourcesResult>

    @JsonRequest("aws/cfn/resources/state")
    fun getResourceState(params: ResourceStateParams): CompletableFuture<ResourceStateResult>

    @JsonRequest("aws/cfn/resources/stackMgmtInfo")
    fun getStackManagementInfo(resourceIdentifier: String): CompletableFuture<ResourceStackManagementResult>

    @JsonRequest("aws/cfn/resources/search")
    fun searchResource(params: SearchResourceParams): CompletableFuture<SearchResourceResult>

    @JsonRequest("aws/cfn/resources/refresh")
    fun refreshResources(params: RefreshResourcesParams): CompletableFuture<RefreshResourcesResult>

    @JsonRequest("aws/cfn/resources/list/remove")
    fun removeResourceType(resourceType: String): CompletableFuture<Void>

    // Validation
    @JsonRequest("aws/cfn/stack/validation/create")
    fun createValidation(params: CreateValidationParams): CompletableFuture<CreateStackActionResult>

    @JsonRequest("aws/cfn/stack/validation/status")
    fun getValidationStatus(params: Identifiable): CompletableFuture<GetStackActionStatusResult>

    @JsonRequest("aws/cfn/stack/validation/status/describe")
    fun describeValidationStatus(params: Identifiable): CompletableFuture<DescribeValidationStatusResult>

    // Stack View
    @JsonRequest("aws/cfn/stack/describe")
    fun describeStack(params: DescribeStackParams): CompletableFuture<DescribeStackResult>
}
