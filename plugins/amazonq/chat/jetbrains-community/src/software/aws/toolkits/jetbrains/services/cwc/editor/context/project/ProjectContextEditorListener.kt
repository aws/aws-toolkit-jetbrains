// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cwc.editor.context.project
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.vfs.VirtualFile
import software.aws.toolkits.jetbrains.services.codewhisperer.settings.CodeWhispererSettings

class ProjectContextEditorListener : FileEditorManagerListener {
    override fun selectionChanged(event: FileEditorManagerEvent) {
        val oldFile = event.oldFile ?: return

        // TODO: should respect isIdeAutosave config
        with(FileDocumentManager.getInstance()) {
            getDocument(oldFile)?.let {
                saveDocument(it)
            }
        }
    }
}
