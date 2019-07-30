// Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cloudformation

import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.project.Project
import icons.AwsIcons
import software.amazon.awssdk.services.cloudformation.CloudFormationClient
import software.amazon.awssdk.services.cloudformation.model.ResourceStatus
import software.amazon.awssdk.services.cloudformation.model.StackStatus
import software.amazon.awssdk.services.lambda.LambdaClient
import software.aws.toolkits.jetbrains.core.AwsClientManager
import software.aws.toolkits.jetbrains.core.AwsResourceCache
import software.aws.toolkits.jetbrains.core.DeleteResourceAction
import software.aws.toolkits.jetbrains.core.awsClient
import software.aws.toolkits.jetbrains.core.explorer.AwsExplorerService
import software.aws.toolkits.jetbrains.core.explorer.nodes.AwsExplorerEmptyNode
import software.aws.toolkits.jetbrains.core.explorer.nodes.AwsExplorerErrorNode
import software.aws.toolkits.jetbrains.core.explorer.nodes.AwsExplorerNode
import software.aws.toolkits.jetbrains.core.explorer.nodes.AwsExplorerResourceNode
import software.aws.toolkits.jetbrains.core.explorer.nodes.AwsExplorerServiceRootNode
import software.aws.toolkits.jetbrains.core.explorer.nodes.ResourceParentNode
import software.aws.toolkits.jetbrains.services.cloudformation.resources.CloudFormationResources
import software.aws.toolkits.jetbrains.core.stack.StackWindowManager
import software.aws.toolkits.jetbrains.services.lambda.LambdaFunctionNode
import software.aws.toolkits.jetbrains.services.lambda.toDataClass
import software.aws.toolkits.jetbrains.utils.TaggingResourceType
import software.aws.toolkits.jetbrains.utils.toHumanReadable
import software.aws.toolkits.resources.message
import java.util.concurrent.CompletableFuture

class CloudFormationServiceNode(project: Project) :
    AwsExplorerServiceRootNode(project, AwsExplorerService.CLOUDFORMATION) {
    override fun getChildrenInternal(): List<AwsExplorerNode<*>> {
        val future = AwsResourceCache.getInstance(nodeProject).getResource(CloudFormationResources.listStacks())
        return future.get().asSequence().filter { it.stackStatus() !in DELETING_STACK_STATES }
            .map { CloudFormationStackNode(nodeProject, it.stackName(), it.stackStatus(), it.stackId()) }
            .toList()
    }

    private companion object {
        val DELETING_STACK_STATES = setOf(StackStatus.DELETE_COMPLETE)
    }
}

class CloudFormationStackNode(
    project: Project,
    val stackName: String,
    private val stackStatus: StackStatus,
    val stackId: String
) : AwsExplorerResourceNode<String>(
        project,
        CloudFormationClient.SERVICE_NAME,
        stackName,
        AwsIcons.Resources.CLOUDFORMATION_STACK
    ), ResourceParentNode {
    override fun resourceType() = "stack"

    override fun resourceArn() = stackId

    override fun displayName() = stackName

    override fun isAlwaysLeaf(): Boolean  = false

    override fun isAlwaysShowPlus(): Boolean = true

    override fun getChildren(): List<AwsExplorerNode<*>> = super<ResourceParentNode>.getChildren()

    override fun getChildrenInternal(): List<AwsExplorerNode<*>> {
        val lambdaClient = nodeProject.awsClient<LambdaClient>(credentialProvider, region)
        val future =
            AwsResourceCache.getInstance(nodeProject).getResource(CloudFormationResources.listStackResources(stackId)) as CompletableFuture
        return future.get().asSequence()
            .filter { it.resourceType() == LAMBDA_FUNCTION_TYPE && it.resourceStatus() in COMPLETE_RESOURCE_STATES }
            .map { resource ->
                // TODO: Enable using cache, and a registry for these mappings of CFN -> real resource
                try {
                    val response = lambdaClient.getFunction { it.functionName(resource.physicalResourceId()) }
                    LambdaFunctionNode(
                        nodeProject,
                        lambdaClient,
                        response.configuration().toDataClass(credentialProvider.id, region),
                        true
                    )
                } catch (e: Exception) {
                    AwsExplorerErrorNode(nodeProject, e)
                }
            }
            .toList()
    }

    override fun emptyChildrenNode(): AwsExplorerEmptyNode = AwsExplorerEmptyNode(
        nodeProject,
        message("explorer.stack.no.serverless.resources")
    )

    override fun statusText(): String? = stackStatus.toString().toHumanReadable()

    private companion object {
        val COMPLETE_RESOURCE_STATES = setOf(ResourceStatus.CREATE_COMPLETE, ResourceStatus.UPDATE_COMPLETE)
    }
}

class DeleteCloudFormationStackAction : DeleteResourceAction<CloudFormationStackNode>(
    message("cloudformation.stack.delete.action"),
    TaggingResourceType.CLOUDFORMATION_STACK
) {
    override fun performDelete(selected: CloudFormationStackNode) {
        val client: CloudFormationClient = AwsClientManager.getInstance(selected.nodeProject).getClient()
        client.deleteStack { it.stackName(selected.stackName) }
        runInEdt {
            StackWindowManager.getInstance(selected.nodeProject).openStack(selected.stackName, selected.stackId)
        }
        client.waitForStackDeletionComplete(selected.stackName)
    }
}