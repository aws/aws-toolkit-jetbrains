// Copyright 2022 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0
package software.aws.toolkits.jetbrains.core.compatability

import com.intellij.workspaceModel.ide.impl.toVirtualFile
import com.intellij.workspaceModel.ide.impl.virtualFile

fun VirtualFileUrl.toVirtualFile() = this.virtualFile
