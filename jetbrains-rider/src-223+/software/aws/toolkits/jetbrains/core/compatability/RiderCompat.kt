// Copyright 2022 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0
package software.aws.toolkits.jetbrains.core.compatability

import com.intellij.workspaceModel.ide.impl.toVirtualFile
import com.intellij.workspaceModel.storage.url.VirtualFileUrl

fun VirtualFileUrl.toVirtualFile() = this.toVirtualFile()

abstract class RiderProjectTemplate : com.jetbrains.rider.projectView.actions.projectTemplating.RiderProjectTemplate {
    override val localizedGroup: String
        get() = group
    override val localizedName: String
        get() = name
}
