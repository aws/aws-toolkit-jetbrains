// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonq.lsp.util

import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VfsUtilCore


object FileUriUtil {

    fun toUriString(virtualFile: VirtualFile): String {
        val file = VfsUtilCore.virtualToIoFile(virtualFile)
        return VfsUtil.toUri(file).toString()

    }
}

