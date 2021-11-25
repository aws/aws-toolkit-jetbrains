// Copyright 2021 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.dynamic.explorer.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import software.aws.toolkits.jetbrains.core.credentials.getConnectionSettingsOrThrow
import software.aws.toolkits.jetbrains.core.explorer.actions.AwsExplorerActionContributor
import software.aws.toolkits.jetbrains.core.explorer.actions.SingleResourceNodeAction
import software.aws.toolkits.jetbrains.core.explorer.nodes.AwsExplorerNode
import software.aws.toolkits.jetbrains.core.explorer.nodes.AwsExplorerResourceNode
import software.aws.toolkits.jetbrains.services.dynamic.DynamicResourceFileManager
import software.aws.toolkits.jetbrains.services.dynamic.DynamicResourceIdentifier
import software.aws.toolkits.jetbrains.services.dynamic.OpenResourceMode
import software.aws.toolkits.resources.message

class ViewResourceConfigurationAction<T>(node: T) :
    SingleResourceNodeAction<T>(message("dynamic_resources.view_configuration_action_title", node.typeDisplayName))
    where T : AwsExplorerResourceNode<*>, T : CloudApiResource {

    override fun actionPerformed(selected: T, e: AnActionEvent) {
        val resourceId = DynamicResourceIdentifier(selected.nodeProject.getConnectionSettingsOrThrow(), selected.cloudApiResourceType, selected.identifier())
        DynamicResourceFileManager.getInstance(selected.nodeProject).openEditor(resourceId, OpenResourceMode.READ)
    }
}

class ViewResourceConfigurationActionContributor : AwsExplorerActionContributor {
    override fun process(group: DefaultActionGroup, node: AwsExplorerNode<*>) {
        if (node is AwsExplorerResourceNode && node is CloudApiResource) {
            group.add(ViewResourceConfigurationAction(node))
        }
    }
}

interface CloudApiResource {
    /**
     * Human readable type name e.g. Bucket, defaults to the 3 part of a CFN type (e.g. AWS::S3::Bucket becomes "Bucket")
     */
    val typeDisplayName: String get() = cloudApiResourceType.substringAfterLast("::")

    val cloudApiResourceType: String

    fun identifier(): String
}
