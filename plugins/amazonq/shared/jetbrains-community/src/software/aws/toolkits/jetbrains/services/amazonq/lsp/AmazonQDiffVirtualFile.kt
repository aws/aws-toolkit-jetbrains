// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonq.lsp

import com.intellij.diff.chains.SimpleDiffRequestChain
import com.intellij.diff.editor.ChainDiffVirtualFile
import com.intellij.diff.editor.DiffEditorTabFilesManager
import com.intellij.diff.requests.SimpleDiffRequest
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import software.aws.toolkits.resources.message

/**
 * A virtual file that represents an AmazonQ diff view.
 * This class allows us to identify diff files created by AmazonQ.
 */
class AmazonQDiffVirtualFile(
    diffChain: SimpleDiffRequestChain,
    name: String,
) : ChainDiffVirtualFile(diffChain, name) {
    companion object {
        fun openDiff(project: Project, diffRequest: SimpleDiffRequest) {
            // Find any existing AmazonQ diff files
            val fileEditorManager = FileEditorManager.getInstance(project)
            val existingDiffFiles = fileEditorManager.openFiles.filterIsInstance<AmazonQDiffVirtualFile>()

            // Close existing diff files
            existingDiffFiles.forEach { fileEditorManager.closeFile(it) }

            // Create and open the new diff file
            val diffChain = SimpleDiffRequestChain(diffRequest)
            val diffVirtualFile = AmazonQDiffVirtualFile(diffChain, diffRequest.title ?: message("aws.q.lsp.client.diff_message"))
            DiffEditorTabFilesManager.getInstance(project).showDiffFile(diffVirtualFile, true)
        }
    }
}
