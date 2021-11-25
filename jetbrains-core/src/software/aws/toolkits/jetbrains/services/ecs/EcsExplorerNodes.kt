// Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.ecs

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.project.Project
import icons.AwsIcons
import software.amazon.awssdk.services.ecs.EcsClient
import software.amazon.awssdk.services.ecs.model.Service
import software.aws.toolkits.jetbrains.core.credentials.getConnectionSettingsOrThrow
import software.aws.toolkits.jetbrains.core.explorer.actions.AwsExplorerActionContributor
import software.aws.toolkits.jetbrains.core.explorer.nodes.AwsExplorerEmptyNode
import software.aws.toolkits.jetbrains.core.explorer.nodes.AwsExplorerNode
import software.aws.toolkits.jetbrains.core.explorer.nodes.AwsExplorerResourceNode
import software.aws.toolkits.jetbrains.core.explorer.nodes.AwsExplorerServiceNode
import software.aws.toolkits.jetbrains.core.explorer.nodes.AwsExplorerServiceRootNode
import software.aws.toolkits.jetbrains.core.explorer.nodes.ResourceLocationNode
import software.aws.toolkits.jetbrains.core.explorer.nodes.ResourceParentNode
import software.aws.toolkits.jetbrains.core.getResourceNow
import software.aws.toolkits.jetbrains.services.dynamic.DynamicResourceFileManager
import software.aws.toolkits.jetbrains.services.dynamic.DynamicResourceIdentifier
import software.aws.toolkits.jetbrains.services.dynamic.explorer.actions.CloudApiResource
import software.aws.toolkits.jetbrains.services.ecs.execution.EcsCloudDebugLocation
import software.aws.toolkits.jetbrains.services.ecs.resources.EcsResources
import software.aws.toolkits.resources.message

class EcsParentNode(project: Project, service: AwsExplorerServiceNode) : AwsExplorerServiceRootNode(project, service) {
    override fun displayName(): String = message("explorer.node.ecs")
    override fun getChildrenInternal(): List<AwsExplorerNode<*>> = listOf(
        EcsClusterParentNode(nodeProject)
    )
}

class EcsClusterParentNode(project: Project) :
    AwsExplorerNode<String>(project, message("ecs.clusters"), null),
    ResourceParentNode {

    override fun isAlwaysShowPlus(): Boolean = true

    override fun getChildren(): List<AwsExplorerNode<*>> = super.getChildren()
    override fun getChildrenInternal(): List<AwsExplorerNode<*>> = nodeProject
        .getResourceNow(EcsResources.LIST_CLUSTER_ARNS)
        .map { EcsClusterNode(nodeProject, it) }
}

class EcsClusterNode(project: Project, private val clusterArn: String) :
    AwsExplorerResourceNode<String>(project, EcsClient.SERVICE_NAME, clusterArn, AwsIcons.Resources.Ecs.ECS_CLUSTER),
    ResourceParentNode,
    CloudApiResource {

    override fun resourceType(): String = "cluster"
    override fun resourceArn(): String = clusterArn
    override fun displayName(): String = clusterArn.split("cluster/", limit = 2).last()
    override fun isAlwaysShowPlus(): Boolean = true
    override fun emptyChildrenNode(): AwsExplorerEmptyNode = AwsExplorerEmptyNode(nodeProject, message("ecs.no_services_in_cluster"))

    override fun getChildren(): List<AwsExplorerNode<*>> = super<ResourceParentNode>.getChildren()
    override fun getChildrenInternal(): List<AwsExplorerNode<*>> = nodeProject
        .getResourceNow(EcsResources.listServiceArns(clusterArn))
        .map { nodeProject.getResourceNow(EcsResources.describeService(clusterArn, it)) }
        .map { EcsServiceNode(nodeProject, it, clusterArn) }

    override val cloudApiResourceType = "AWS::ECS::Cluster"
    override fun identifier() = clusterArn
}

class EcsServiceNode(project: Project, internal val service: Service, private val clusterArn: String) :
    AwsExplorerResourceNode<Service>(project, EcsClient.SERVICE_NAME, service, AwsIcons.Resources.Ecs.ECS_SERVICE),
    ResourceLocationNode,
    CloudApiResource {

    override fun resourceType() = "service"
    override fun resourceArn(): String = value.serviceArn()
    override fun displayName(): String = value.serviceName()
    override fun location() = EcsCloudDebugLocation(nodeProject, service)
    fun executeCommandEnabled(): Boolean = value.enableExecuteCommand()
    fun clusterArn(): String = clusterArn

    override val cloudApiResourceType = "AWS::ECS::Service"
    override fun identifier() = "${resourceArn()}|$clusterArn"
}

class ViewTaskDefinitionAction(private val node: EcsServiceNode) : AnAction(message("dynamic_resources.view_configuration_action_title", "Task Definition")) {
    override fun actionPerformed(e: AnActionEvent) {
        val identifier = DynamicResourceIdentifier(node.nodeProject.getConnectionSettingsOrThrow(), "AWS::ECS::TaskDefinition", node.service.taskDefinition())
        DynamicResourceFileManager.getInstance(node.nodeProject).openEditor(identifier)
    }
}

class EcsTaskDefinitionActionContributor : AwsExplorerActionContributor {
    override fun process(group: DefaultActionGroup, node: AwsExplorerNode<*>) {
        if (node is EcsServiceNode) {
            group.add(ViewTaskDefinitionAction(node))
        }
    }
}
