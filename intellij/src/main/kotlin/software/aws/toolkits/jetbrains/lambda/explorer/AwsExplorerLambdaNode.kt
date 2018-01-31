package software.aws.toolkits.jetbrains.lambda.explorer

import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.openapi.project.Project
import software.amazon.awssdk.services.lambda.LambdaClient
import software.amazon.awssdk.services.lambda.model.FunctionConfiguration
import software.amazon.awssdk.services.lambda.model.ListFunctionsRequest
import software.aws.toolkits.jetbrains.core.AwsClientManager
import software.aws.toolkits.jetbrains.ui.LAMBDA_SERVICE_ICON
import software.aws.toolkits.jetbrains.ui.SQS_QUEUE_ICON
import software.aws.toolkits.jetbrains.ui.explorer.AwsExplorerNode
import software.aws.toolkits.jetbrains.ui.explorer.AwsExplorerServiceRootNode
import software.aws.toolkits.jetbrains.ui.explorer.AwsTruncatedResultNode

class AwsExplorerLambdaRootNode(project: Project) :
        AwsExplorerServiceRootNode(project, "AWS Lambda", LAMBDA_SERVICE_ICON) {

    private val client: LambdaClient = AwsClientManager.getInstance(project).getClient()

    override fun loadResources(paginationToken: String?): Collection<AwsExplorerNode<*>> {
        val request = ListFunctionsRequest.builder()
        paginationToken?.let { request.marker(paginationToken) }

        val response = client.listFunctions(request.build())
        val resources: MutableList<AwsExplorerNode<*>> = response.functions().map { mapResourceToNode(it) }.toMutableList()
        response.nextMarker()?.let {
            resources.add(AwsTruncatedResultNode(this, it))
        }

        return resources
    }

    private fun mapResourceToNode(resource: FunctionConfiguration) = AwsExplorerFunctionNode(project!!, resource)
}

class AwsExplorerFunctionNode(project: Project, private val function: FunctionConfiguration) :
        AwsExplorerNode<FunctionConfiguration>(project, function, SQS_QUEUE_ICON) { //TODO replace to Function icon

    override fun getChildren(): Collection<AbstractTreeNode<Any>> {
        return emptyList()
    }

    override fun toString(): String {
        return function.functionName()
    }
}