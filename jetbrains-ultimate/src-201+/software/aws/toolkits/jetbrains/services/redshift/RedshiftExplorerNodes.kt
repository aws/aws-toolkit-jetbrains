// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.redshift

import icons.AwsIcons
import com.intellij.openapi.project.Project
import software.amazon.awssdk.services.redshift.RedshiftClient
import software.amazon.awssdk.services.redshift.model.Cluster
import software.aws.toolkits.jetbrains.core.explorer.nodes.AwsExplorerNode
import software.aws.toolkits.jetbrains.core.explorer.nodes.AwsExplorerResourceNode
import software.aws.toolkits.jetbrains.core.explorer.nodes.AwsExplorerServiceNode
import software.aws.toolkits.jetbrains.core.explorer.nodes.CacheBackedAwsExplorerServiceRootNode

class RedshiftExplorerParentNode(
    project: Project,
    service: AwsExplorerServiceNode
) : CacheBackedAwsExplorerServiceRootNode<Cluster>(project, service, RedshiftResources.LIST_CLUSTERS) {
    override fun toNode(child: Cluster): AwsExplorerNode<*> = RedshiftExplorerNode(nodeProject, child)
}

class RedshiftExplorerNode(project: Project, val cluster: Cluster) : AwsExplorerResourceNode<Cluster>(
    project,
    RedshiftClient.SERVICE_NAME,
    cluster,
    AwsIcons.Resources.REDSHIFT
) {
    override fun displayName(): String = cluster.clusterIdentifier()
    override fun resourceType(): String = "cluster"
    override fun resourceArn(): String = nodeProject.clusterArn(cluster, region)
}
