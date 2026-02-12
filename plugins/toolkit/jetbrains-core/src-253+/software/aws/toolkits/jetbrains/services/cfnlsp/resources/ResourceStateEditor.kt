// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cfnlsp.resources

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project

@Service(Service.Level.PROJECT)
internal class ResourceStateEditor(private val project: Project) {
    fun insertAtCaret(text: String) {
        val editor = FileEditorManager.getInstance(project).selectedTextEditor ?: return
        ApplicationManager.getApplication().invokeLater {
            WriteCommandAction.runWriteCommandAction(project) {
                editor.document.insertString(editor.caretModel.offset, text)
            }
        }
    }

    fun getActiveDocumentUri(): String? =
        FileEditorManager.getInstance(project).selectedFiles.firstOrNull()?.url

    fun getActiveEditor() = FileEditorManager.getInstance(project).selectedTextEditor

    companion object {
        fun getInstance(project: Project): ResourceStateEditor = project.service()
    }
}
