// Copyright 2021 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.dynamo.explorer

import com.intellij.openapi.project.Project
import icons.AwsIcons
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.aws.toolkits.core.utils.tryOrNull
import software.aws.toolkits.jetbrains.core.credentials.activeRegion
import software.aws.toolkits.jetbrains.core.explorer.nodes.AwsExplorerNode
import software.aws.toolkits.jetbrains.core.explorer.nodes.AwsExplorerResourceNode
import software.aws.toolkits.jetbrains.core.explorer.nodes.AwsExplorerServiceNode
import software.aws.toolkits.jetbrains.core.explorer.nodes.CacheBackedAwsExplorerServiceRootNode
import software.aws.toolkits.jetbrains.core.getResourceIfPresent
import software.aws.toolkits.jetbrains.services.dynamo.DynamoResources
import software.aws.toolkits.jetbrains.services.dynamo.editor.DynamoTableEditorProvider
import software.aws.toolkits.jetbrains.services.sts.StsResources
import software.aws.toolkits.resources.message

class DynamoServiceNode(project: Project, service: AwsExplorerServiceNode) :
    CacheBackedAwsExplorerServiceRootNode<String>(project, service, DynamoResources.LIST_TABLES) {
    override fun displayName(): String = message("explorer.node.dynamo")
    override fun toNode(child: String): AwsExplorerNode<*> = DynamoTableNode(nodeProject, child)
}

class DynamoTableNode(project: Project, private val tableName: String) :
    AwsExplorerResourceNode<String>(project, DynamoDbClient.SERVICE_METADATA_ID, tableName, AwsIcons.Resources.Dynamo.TABLE) {
    override fun displayName(): String = tableName
    override fun resourceType(): String = "table"

    override fun resourceArn(): String {
        val account = tryOrNull { nodeProject.getResourceIfPresent(StsResources.ACCOUNT) } ?: ""
        return "arn:${nodeProject.activeRegion().partitionId}:dynamodb:${nodeProject.activeRegion().id}:$account:table/$tableName"
    }

    override fun onDoubleClick() {
        DynamoTableEditorProvider.openViewer(nodeProject, resourceArn())
    }
}
