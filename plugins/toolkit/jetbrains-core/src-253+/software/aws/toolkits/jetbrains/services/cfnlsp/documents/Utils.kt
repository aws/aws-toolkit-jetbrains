// Copyright 2026 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

@file:Suppress("Filename")

package software.aws.toolkits.jetbrains.services.cfnlsp.documents

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtil
import java.net.URI

internal object RelativePathParser {
    fun getRelativePath(uri: String, project: Project): String =
        try {
            val file = VfsUtil.findFileByURL(URI(uri).toURL())
            file?.let { VfsUtil.getRelativePath(it, project.baseDir) } ?: uri
        } catch (_: Exception) {
            uri
        }
}
