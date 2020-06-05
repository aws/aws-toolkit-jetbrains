package software.aws.toolkits.jetbrains.core.explorer.nodes

import com.intellij.openapi.project.Project
import software.amazon.awssdk.services.rds.RdsClient
import software.aws.toolkits.jetbrains.services.rds.auth.RdsExplorerParentNode
import software.aws.toolkits.resources.message

class RdsExplorerRootNode : AwsExplorerServiceNode {
    override val serviceId: String = RdsClient.SERVICE_NAME
    override val displayName: String = message("explorer.node.rds")

    override fun buildServiceRootNode(project: Project) = RdsExplorerParentNode(project, this)
}
