// Copyright 2018 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cloudformation

import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.openapi.project.Project
import icons.AwsIcons
import software.amazon.awssdk.services.cloudformation.CloudFormationClient
import software.amazon.awssdk.services.cloudformation.model.ResourceStatus
import software.amazon.awssdk.services.cloudformation.model.StackResource
import software.amazon.awssdk.services.cloudformation.model.StackStatus
import software.amazon.awssdk.services.lambda.LambdaClient
import software.aws.toolkits.jetbrains.core.AwsClientManager
import software.aws.toolkits.jetbrains.core.DeleteResourceAction
import software.aws.toolkits.jetbrains.core.awsClient
import software.aws.toolkits.jetbrains.core.explorer.AwsExplorerEmptyNode
import software.aws.toolkits.jetbrains.core.explorer.AwsExplorerLoadingNode
import software.aws.toolkits.jetbrains.core.explorer.AwsExplorerNode
import software.aws.toolkits.jetbrains.core.explorer.AwsExplorerPageableNode
import software.aws.toolkits.jetbrains.core.explorer.AwsExplorerResourceNode
import software.aws.toolkits.jetbrains.core.explorer.AwsTruncatedResultNode
import software.aws.toolkits.jetbrains.utils.toHumanReadable
import software.aws.toolkits.resources.message

class CloudFormationStacksNode(project: Project) : AwsExplorerPageableNode<String>(project, message("cloudformation.stacks"), null) {

    private val client: CloudFormationClient = AwsClientManager.getInstance(project).getClient()

    override fun loadResources(paginationToken: String?): Collection<AwsExplorerNode<*>> {
        val response = client.describeStacks { request ->
            paginationToken?.let { request.nextToken(it) }
        }

        val nodes = response.stacks().filterNotNull().asSequence()
            .filter {
                !(it.stackStatus() in DELETING_STACK_STATES)
            }
            .sortedBy { it.stackName() }
            .map { stack ->
                CloudFormationStackNode(nodeProject, stack.stackName(), stack.stackStatus())
            }.toList()

        return nodes + paginationNodeIfRequired(response.nextToken())
    }

    private fun paginationNodeIfRequired(nextToken: String?) = when {
        nextToken != null -> listOf(AwsTruncatedResultNode(this, nextToken))
        else -> emptyList()
    }

    private companion object {
        val DELETING_STACK_STATES = setOf(StackStatus.DELETE_COMPLETE)
    }
}

class CloudFormationStackNode(project: Project, val stackName: String, private val stackStatus: StackStatus) :
    AwsExplorerResourceNode<String>(project, CloudFormationClient.SERVICE_NAME, stackName, AwsIcons.Resources.SERVERLESS_APP) {
    init {
        presentation.tooltip = message("cloudformation.stack.status", stackStatus)
    }

    override fun resourceType() = "stack"

    private val cfnClient: CloudFormationClient = project.awsClient()

    /**
     * CloudFormation Stack Nodes do not immediately query for stack resources.
     * When the node is added to a tree, getChildren is called immediately, to determine if the node contains any children or not.
     * Accounts with many stacks would cause many describeStackResources requests, potentially triggering TPS limits.
     * Instead, we use a placeholder "loading" node, and swap this out the first time the node is expanded.
     */
    private val loadingChildren: Collection<AbstractTreeNode<Any>> = listOf(AwsExplorerLoadingNode(project)).filterIsInstance<AbstractTreeNode<Any>>()
    private val noResourcesChildren: Collection<AbstractTreeNode<Any>> = listOf(AwsExplorerEmptyNode(project, message("explorer.stack.no.serverless.resources"))).filterIsInstance<AbstractTreeNode<Any>>()
    private var cachedChildren: Collection<AbstractTreeNode<Any>> = if (stackStatus in FAILED_STACK_STATES || stackStatus in IN_PROGRESS_STACK_STATES) {
        emptyList()
    } else {
        loadingChildren
    }

    var isChildCacheInInitialState: Boolean = true

    /**
     * Children are cached by default to prevent describeStackResources from being called each time a stack node is expanded.
     */
    @Suppress("UNCHECKED_CAST")
    override fun getChildren(): Collection<AbstractTreeNode<Any>> = getChildren(false)

    fun getChildren(refresh: Boolean = false): Collection<AbstractTreeNode<Any>> {
        if (refresh) {
            updateCachedChildren()
        }

        return cachedChildren
    }

    private fun updateCachedChildren() {
        cachedChildren = if (stackStatus in FAILED_STACK_STATES || stackStatus in IN_PROGRESS_STACK_STATES) {
            emptyList()
        } else {
            val loaded = loadServerlessStackResources()

            if (loaded.isEmpty()) {
                noResourcesChildren
            } else {
                loaded.filterIsInstance<AbstractTreeNode<Any>>()
            }
        }

        isChildCacheInInitialState = false
    }

    private fun loadServerlessStackResources(): List<CloudFormationStackResourceNode> = cfnClient
        .describeStackResources {
            it.stackName(stackName)
        }
        .stackResources()
        .filter {
            it.resourceType() == LAMBDA_FUNCTION_TYPE && it.resourceStatus() in COMPLETE_RESOURCE_STATES
        }
        .map {
            CloudFormationStackResourceNode(nodeProject, it)
        }.toList()

    override fun statusText(): String? = stackStatus.toString().toHumanReadable()

    private companion object {
        val COMPLETE_RESOURCE_STATES = setOf(ResourceStatus.CREATE_COMPLETE, ResourceStatus.UPDATE_COMPLETE)
        val FAILED_STACK_STATES = setOf(StackStatus.CREATE_FAILED, StackStatus.DELETE_FAILED, StackStatus.ROLLBACK_FAILED)
        val IN_PROGRESS_STACK_STATES = setOf(
            StackStatus.CREATE_IN_PROGRESS,
            StackStatus.DELETE_IN_PROGRESS,
            StackStatus.ROLLBACK_IN_PROGRESS,
            StackStatus.UPDATE_IN_PROGRESS,
            StackStatus.UPDATE_ROLLBACK_IN_PROGRESS
        )
    }
}

open class CloudFormationStackResourceNode(
    project: Project,
    val stackResource: StackResource
) : AwsExplorerResourceNode<StackResource>(project, LambdaClient.SERVICE_NAME, stackResource, AwsIcons.Resources.LAMBDA_FUNCTION) {
    override fun resourceType() = "cloudformation.stack.resource"

    override fun toString(): String = functionName()

    override fun displayName() = functionName()

    fun functionName(): String = stackResource.logicalResourceId()
}

class DeleteCloudFormationStackAction : DeleteResourceAction<CloudFormationStackNode>(message("cloudformation.stack.delete.action")) {
    override fun performDelete(selected: CloudFormationStackNode) {
        val client: CloudFormationClient = AwsClientManager.getInstance(selected.nodeProject).getClient()
        client.deleteStack { it.stackName(selected.stackName) }
        client.waitForStackDeletionComplete(selected.stackName)
    }
}