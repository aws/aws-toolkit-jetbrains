// Copyright 2026 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

@file:Suppress("Filename")

package software.aws.toolkits.jetbrains.services.cfnlsp.documents

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtil
import software.aws.toolkit.core.utils.getLogger
import software.aws.toolkit.core.utils.warn
import java.net.URI

internal object RelativePathParser {
    private val LOG = getLogger<RelativePathParser>()

    fun getRelativePath(uri: String, project: Project): String =
        try {
            val file = VfsUtil.findFileByURL(URI(uri).toURL())
            file?.let { VfsUtil.getRelativePath(it, project.baseDir) } ?: uri
        } catch (e: Exception) {
            LOG.warn(e) { "Failed to get relative path for URI: $uri" }
            uri
        }
}
