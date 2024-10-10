// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonq.project
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener

class ProjectContextEditorListener : FileEditorManagerListener {
    override fun selectionChanged(event: FileEditorManagerEvent) {
        val project = event.manager.project
        val oldFile = event.oldFile ?: return

        // TODO: should run under BGT
        ProjectContextController.getInstance(project).updateIndex(listOf(oldFile.path), ProjectContextController.IndexUpdateMode.UPDATE)
    }
}
