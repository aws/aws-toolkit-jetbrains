// Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0
package software.aws.toolkits.jetbrains.services.s3.BucketActions

import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import icons.AwsIcons
import software.amazon.awssdk.services.s3.S3Client
import software.aws.toolkits.jetbrains.core.explorer.SingleResourceNodeAction
import software.aws.toolkits.jetbrains.core.explorer.nodes.AwsExplorerResourceNode

abstract class OpenBucketViewerAction<in T : AwsExplorerResourceNode<*>>(text: String) : SingleResourceNodeAction<T>(text, icon = AwsIcons.Actions.LAMBDA_FUNCTION_NEW),
        DumbAware {
    abstract fun openEditor(selected: T, client: S3Client, project: Project)
}