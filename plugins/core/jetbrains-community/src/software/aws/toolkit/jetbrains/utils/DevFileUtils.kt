// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkit.jetbrains.utils

import com.intellij.openapi.vfs.VirtualFile

fun isDevFile(file: VirtualFile): Boolean =
    file.name.matches(Regex("devfile\\.ya?ml", RegexOption.IGNORE_CASE))

fun isWorkspaceDevFile(file: VirtualFile, addressableRoot: VirtualFile): Boolean =
    isDevFile(file) && file.parent?.path == addressableRoot.path

fun getWorkspaceDevFile(addressableRoot: VirtualFile): VirtualFile? =
    addressableRoot.children.find { isDevFile(it) }
