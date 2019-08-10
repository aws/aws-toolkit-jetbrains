// Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0
package software.aws.toolkits.jetbrains.services.s3.bucketActions

import com.intellij.icons.AllIcons
import com.intellij.openapi.project.DumbAware
import software.aws.toolkits.jetbrains.core.explorer.SingleResourceNodeAction
import software.aws.toolkits.jetbrains.core.explorer.nodes.AwsExplorerResourceNode

abstract class CopyAction<in T : AwsExplorerResourceNode<*>>(text: String) : SingleResourceNodeAction<T>(text, icon = AllIcons.Actions.Copy),
        DumbAware {
    abstract fun performCopy(selected: T)
}
