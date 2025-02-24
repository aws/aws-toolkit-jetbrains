// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonq.lsp.util

import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import software.aws.toolkits.core.utils.getLogger
import software.aws.toolkits.core.utils.warn
import java.io.File
import java.net.URI
import java.net.URISyntaxException

object FileUriUtil {

    fun toUriString(virtualFile: VirtualFile): String? {
        val protocol = virtualFile.fileSystem.protocol
        val uri = when (protocol) {
            "jar" -> VfsUtilCore.convertToURL(virtualFile.url)?.toExternalForm()
            "jrt" -> virtualFile.url
            else -> toUri(VfsUtilCore.virtualToIoFile(virtualFile)).toASCIIString()
        } ?: return null

        return if (virtualFile.isDirectory) {
            uri.trimEnd('/', '\\')
        } else {
            uri
        }
    }

    private fun toUri(file: File): URI {
        try {
            // URI scheme specified by language server protocol
            return URI("file", "", file.absoluteFile.toURI().path, null)
        } catch (e: URISyntaxException) {
            LOG.warn { "${e.localizedMessage}: $e" }
            return file.absoluteFile.toURI()
        }
    }

    private val LOG = getLogger<FileUriUtil>()
}
