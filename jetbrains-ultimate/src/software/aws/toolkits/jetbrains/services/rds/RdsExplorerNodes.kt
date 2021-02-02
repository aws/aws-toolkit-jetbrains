// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.rds

import com.intellij.openapi.project.Project
import software.amazon.awssdk.services.rds.RdsClient
import software.amazon.awssdk.services.rds.model.DBCluster
import software.aws.toolkits.jetbrains.core.explorer.nodes.AwsExplorerNode
import software.aws.toolkits.jetbrains.core.explorer.nodes.AwsExplorerResourceNode
import software.aws.toolkits.jetbrains.core.explorer.nodes.AwsExplorerServiceNode
import software.aws.toolkits.jetbrains.core.explorer.nodes.AwsExplorerServiceRootNode
import software.aws.toolkits.jetbrains.core.getResourceNow
import software.aws.toolkits.jetbrains.services.rds.resources.LIST_SUPPORTED_CLUSTERS

class RdsExplorerParentNode(project: Project, service: AwsExplorerServiceNode) : AwsExplorerServiceRootNode(project, service) {
    override fun getChildrenInternal(): List<AwsExplorerNode<*>> = nodeProject.getResourceNow(LIST_SUPPORTED_CLUSTERS).map {
        RdsNode(nodeProject, it)
    }
}

class RdsNode(project: Project, val dbCluster: DBCluster, private val rdsEngine: RdsEngine = dbCluster.rdsEngine()) : AwsExplorerResourceNode<String>(
    project,
    RdsClient.SERVICE_NAME,
    dbCluster.dbClusterArn(),
    rdsEngine.icon
) {
    override fun displayName(): String = dbCluster.dbClusterIdentifier()
    override fun resourceArn(): String = dbCluster.dbClusterArn()
    override fun resourceType(): String = "instance"
    override fun statusText(): String? = rdsEngine.additionalInfo

    fun iamAuthEnabled(): Boolean = dbCluster.iamDatabaseAuthenticationEnabled()
}
