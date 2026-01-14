// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.core.explorer.nodes

import com.intellij.openapi.project.Project
import software.aws.toolkit.core.region.AwsRegion
import software.aws.toolkit.jetbrains.core.region.AwsRegionProvider

interface AwsExplorerServiceNode {
    val serviceId: String
    fun buildServiceRootNode(project: Project): AwsExplorerNode<*>
    fun enabled(region: AwsRegion): Boolean = AwsRegionProvider.getInstance().isServiceSupported(region, serviceId)
}
