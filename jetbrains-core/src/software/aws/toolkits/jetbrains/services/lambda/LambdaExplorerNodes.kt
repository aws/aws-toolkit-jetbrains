// Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.lambda

import com.intellij.openapi.project.Project
import com.intellij.psi.NavigatablePsiElement
import icons.AwsIcons
import software.amazon.awssdk.services.lambda.LambdaClient
import software.amazon.awssdk.services.lambda.model.FunctionConfiguration
import software.aws.toolkits.jetbrains.core.explorer.filters.CloudFormationResourceNode
import software.aws.toolkits.jetbrains.core.explorer.filters.CloudFormationResourceParentNode
import software.aws.toolkits.jetbrains.core.explorer.nodes.AwsExplorerNode
import software.aws.toolkits.jetbrains.core.explorer.nodes.AwsExplorerResourceNode
import software.aws.toolkits.jetbrains.core.explorer.nodes.AwsExplorerServiceNode
import software.aws.toolkits.jetbrains.core.explorer.nodes.CacheBackedAwsExplorerServiceRootNode
import software.aws.toolkits.jetbrains.core.explorer.nodes.ResourceLocationNode
import software.aws.toolkits.jetbrains.services.lambda.execution.remote.RemoteLambdaLocation
import software.aws.toolkits.jetbrains.services.lambda.resources.LambdaResources
import software.aws.toolkits.resources.cloudformation.AWS
import software.aws.toolkits.resources.message

class LambdaServiceNode(project: Project, service: AwsExplorerServiceNode) :
    CacheBackedAwsExplorerServiceRootNode<FunctionConfiguration>(project, service, LambdaResources.LIST_FUNCTIONS), CloudFormationResourceParentNode {
    override fun displayName(): String = message("explorer.node.lambda")
    override fun toNode(child: FunctionConfiguration): AwsExplorerNode<*> = LambdaFunctionNode(nodeProject, child.toDataClass())
    override fun cfnResourceTypes() = setOf(AWS.Lambda.Function)
}

open class LambdaFunctionNode(
    project: Project,
    function: LambdaFunction
) : AwsExplorerResourceNode<LambdaFunction>(
    project,
    LambdaClient.SERVICE_NAME,
    function,
    AwsIcons.Resources.LAMBDA_FUNCTION
),
    ResourceLocationNode,
    CloudFormationResourceNode {

    override fun resourceType() = "function"

    override fun resourceArn() = value.arn
    override val resourceType = AWS.Lambda.Function
    override val cfnPhysicalIdentifier: String = functionName()

    override fun toString(): String = functionName()

    override fun displayName() = functionName()

    override fun location() = RemoteLambdaLocation(nodeProject, value)

    fun functionName(): String = value.name

    fun handlerPsi(): Array<NavigatablePsiElement> {
        val runtime = value.runtime ?: return emptyArray()
        val handler = value.handler ?: return emptyArray()
        return Lambda.findPsiElementsForHandler(nodeProject, runtime, handler)
    }
}
