// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cfnlsp

import org.eclipse.lsp4j.jsonrpc.services.JsonRequest
import org.eclipse.lsp4j.services.LanguageServer
import software.aws.toolkits.jetbrains.services.cfnlsp.protocol.CreateDeploymentParams
import software.aws.toolkits.jetbrains.services.cfnlsp.protocol.CreateStackActionResult
import software.aws.toolkits.jetbrains.services.cfnlsp.protocol.CreateValidationParams
import software.aws.toolkits.jetbrains.services.cfnlsp.protocol.DeleteChangeSetParams
import software.aws.toolkits.jetbrains.services.cfnlsp.protocol.DescribeChangeSetParams
import software.aws.toolkits.jetbrains.services.cfnlsp.protocol.DescribeChangeSetResult
import software.aws.toolkits.jetbrains.services.cfnlsp.protocol.DescribeDeletionStatusResult
import software.aws.toolkits.jetbrains.services.cfnlsp.protocol.DescribeDeploymentStatusResult
import software.aws.toolkits.jetbrains.services.cfnlsp.protocol.DescribeStackParams
import software.aws.toolkits.jetbrains.services.cfnlsp.protocol.DescribeStackResult
import software.aws.toolkits.jetbrains.services.cfnlsp.protocol.DescribeValidationStatusResult
import software.aws.toolkits.jetbrains.services.cfnlsp.protocol.GetCapabilitiesResult
import software.aws.toolkits.jetbrains.services.cfnlsp.protocol.GetParametersResult
import software.aws.toolkits.jetbrains.services.cfnlsp.protocol.GetStackActionStatusResult
import software.aws.toolkits.jetbrains.services.cfnlsp.protocol.GetStackResourcesParams
import software.aws.toolkits.jetbrains.services.cfnlsp.protocol.GetTemplateArtifactsResult
import software.aws.toolkits.jetbrains.services.cfnlsp.protocol.GetTemplateResourcesResult
import software.aws.toolkits.jetbrains.services.cfnlsp.protocol.Identifiable
import software.aws.toolkits.jetbrains.services.cfnlsp.protocol.ListChangeSetsParams
import software.aws.toolkits.jetbrains.services.cfnlsp.protocol.ListChangeSetsResult
import software.aws.toolkits.jetbrains.services.cfnlsp.protocol.ListResourcesParams
import software.aws.toolkits.jetbrains.services.cfnlsp.protocol.ListResourcesResult
import software.aws.toolkits.jetbrains.services.cfnlsp.protocol.ListStackResourcesResult
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

    @JsonRequest("aws/cfn/stack/validation/create")
    fun createValidation(params: CreateValidationParams): CompletableFuture<CreateStackActionResult>

    @JsonRequest("aws/cfn/stack/validation/status")
    fun getValidationStatus(params: Identifiable): CompletableFuture<GetStackActionStatusResult>

    @JsonRequest("aws/cfn/stack/validation/status/describe")
    fun describeValidationStatus(params: Identifiable): CompletableFuture<DescribeValidationStatusResult>

    @JsonRequest("aws/cfn/stack/changeSet/describe")
    fun describeChangeSet(params: DescribeChangeSetParams): CompletableFuture<DescribeChangeSetResult>

    @JsonRequest("aws/cfn/stack/changeSet/delete")
    fun deleteChangeSet(params: DeleteChangeSetParams): CompletableFuture<CreateStackActionResult>

    @JsonRequest("aws/cfn/stack/changeSet/deletion/status")
    fun getChangeSetDeletionStatus(params: Identifiable): CompletableFuture<GetStackActionStatusResult>

    @JsonRequest("aws/cfn/stack/changeSet/deletion/status/describe")
    fun describeChangeSetDeletionStatus(params: Identifiable): CompletableFuture<DescribeDeletionStatusResult>

    @JsonRequest("aws/cfn/stack/deployment/create")
    fun createDeployment(params: CreateDeploymentParams): CompletableFuture<CreateStackActionResult>

    @JsonRequest("aws/cfn/stack/deployment/status")
    fun getDeploymentStatus(params: Identifiable): CompletableFuture<GetStackActionStatusResult>

    @JsonRequest("aws/cfn/stack/deployment/status/describe")
    fun describeDeploymentStatus(params: Identifiable): CompletableFuture<DescribeDeploymentStatusResult>

    @JsonRequest("aws/cfn/stack/parameters")
    fun getParameters(uri: String): CompletableFuture<GetParametersResult>

    @JsonRequest("aws/cfn/stack/capabilities")
    fun getCapabilities(uri: String): CompletableFuture<GetCapabilitiesResult>

    @JsonRequest("aws/cfn/stack/import/resources")
    fun getTemplateResources(uri: String): CompletableFuture<GetTemplateResourcesResult>

    @JsonRequest("aws/cfn/stack/template/artifacts")
    fun getTemplateArtifacts(uri: String): CompletableFuture<GetTemplateArtifactsResult>

    @JsonRequest("aws/cfn/stack/describe")
    fun describeStack(params: DescribeStackParams): CompletableFuture<DescribeStackResult>

    @JsonRequest("aws/cfn/stack/resources")
    fun getStackResources(params: GetStackResourcesParams): CompletableFuture<ListStackResourcesResult>
}
