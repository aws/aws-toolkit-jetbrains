// Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.lambda

import com.intellij.openapi.project.Project
import com.intellij.psi.NavigatablePsiElement
import icons.AwsIcons
import software.amazon.awssdk.services.lambda.LambdaClient
import software.amazon.awssdk.services.lambda.model.FunctionConfiguration
import software.aws.toolkits.jetbrains.core.AwsClientManager
import software.aws.toolkits.jetbrains.core.AwsResourceCache
import software.aws.toolkits.jetbrains.core.explorer.AwsExplorerService
import software.aws.toolkits.jetbrains.core.explorer.nodes.AwsExplorerNode
import software.aws.toolkits.jetbrains.core.explorer.nodes.AwsExplorerResourceNode
import software.aws.toolkits.jetbrains.core.explorer.nodes.AwsExplorerServiceRootNode
import software.aws.toolkits.jetbrains.services.lambda.resources.LambdaResources
import java.util.concurrent.CompletableFuture

class LambdaServiceNode(project: Project) : AwsExplorerServiceRootNode(project, AwsExplorerService.LAMBDA) {
    private val client: LambdaClient = AwsClientManager.getInstance(project).getClient()

    override fun getChildrenInternal(): List<AwsExplorerNode<*>> {
        val future =
            AwsResourceCache.getInstance(nodeProject).getResource(LambdaResources.LIST_FUNCTIONS) as CompletableFuture
        return future.get()
            .map { mapResourceToNode(it) }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.functionName() })
    }

    private fun mapResourceToNode(resource: FunctionConfiguration) =
        LambdaFunctionNode(nodeProject, client, resource.toDataClass(credentialProvider.id, region))
}

open class LambdaFunctionNode(
    project: Project,
    val client: LambdaClient,
    val function: LambdaFunction,
    immutable: Boolean = false
) : AwsExplorerResourceNode<LambdaFunction>(
    project,
    LambdaClient.SERVICE_NAME,
    function,
    AwsIcons.Resources.LAMBDA_FUNCTION,
    immutable
) {
    override fun resourceType() = "function"

    override fun resourceArn() = function.arn

    override fun toString(): String = functionName()

    override fun displayName() = functionName()

    fun functionName(): String = function.name

    fun handlerPsi(): Array<NavigatablePsiElement> =
        Lambda.findPsiElementsForHandler(super.getProject()!!, function.runtime, function.handler)
}