// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.core.explorer.nodes

import com.intellij.openapi.project.Project
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.aws.toolkits.jetbrains.services.dynamo.explorer.DynamoServiceNode

class DynamoExplorerRootNode : AwsExplorerServiceNode {
    override val serviceId: String = DynamoDbClient.SERVICE_NAME
    override fun buildServiceRootNode(project: Project) = DynamoServiceNode(project, this)
}
