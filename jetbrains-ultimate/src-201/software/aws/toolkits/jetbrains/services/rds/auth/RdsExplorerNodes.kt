package software.aws.toolkits.jetbrains.services.rds.auth

import com.intellij.openapi.project.Project
import icons.AwsIcons
import software.amazon.awssdk.services.rds.RdsClient
import software.amazon.awssdk.services.rds.model.DBInstance
import software.aws.toolkits.jetbrains.core.AwsResourceCache
import software.aws.toolkits.jetbrains.core.Resource
import software.aws.toolkits.jetbrains.core.explorer.nodes.AwsExplorerNode
import software.aws.toolkits.jetbrains.core.explorer.nodes.AwsExplorerResourceNode
import software.aws.toolkits.jetbrains.core.explorer.nodes.AwsExplorerServiceNode
import software.aws.toolkits.jetbrains.core.explorer.nodes.AwsExplorerServiceRootNode
import software.aws.toolkits.jetbrains.core.explorer.nodes.ResourceParentNode
import software.aws.toolkits.jetbrains.services.rds.RdsResources
import software.aws.toolkits.resources.message

class RdsExplorerParentNode(project: Project, service: AwsExplorerServiceNode) : AwsExplorerServiceRootNode(project, service) {
    override fun getChildrenInternal(): List<AwsExplorerNode<*>> = listOf(
        RdsParentNode(nodeProject, message("rds.mysql"), RdsResources.LIST_INSTANCES_MYSQL),
        RdsParentNode(nodeProject, message("rds.postgres"), RdsResources.LIST_INSTANCES_POSTGRES)
    )
}

class RdsParentNode(
    project: Project,
    type: String,
    private val method: Resource.Cached<List<DBInstance>>
) : AwsExplorerNode<String>(project, type, null),
    ResourceParentNode {
    override fun isAlwaysShowPlus(): Boolean = true

    override fun getChildren(): List<AwsExplorerNode<*>> = super.getChildren()
    override fun getChildrenInternal(): List<AwsExplorerNode<*>> = AwsResourceCache.getInstance(nodeProject)
        .getResourceNow(method)
        .map { RdsNode(nodeProject, it) }
        .toMutableList()
}

class RdsNode(project: Project, private val instance: DBInstance) : AwsExplorerResourceNode<String>(
    project,
    RdsClient.SERVICE_NAME,
    instance.dbInstanceArn(),
    AwsIcons.Resources.Ecs.ECS_CLUSTER
) {
    override fun displayName(): String = instance.dbInstanceIdentifier()
    override fun resourceArn(): String = instance.dbInstanceArn()
    override fun resourceType(): String = instance.engine()
}
