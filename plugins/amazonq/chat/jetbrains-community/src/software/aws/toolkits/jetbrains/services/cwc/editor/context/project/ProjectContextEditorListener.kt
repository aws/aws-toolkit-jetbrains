// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cwc.editor.context.project
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.vfs.VirtualFile


class ProjectContextEditorListener: FileEditorManagerListener {
    override fun fileClosed(source: FileEditorManager, file: VirtualFile) {
        ProjectContextProvider.getInstance(source.project).updateIndex(file.path)
    }
}

