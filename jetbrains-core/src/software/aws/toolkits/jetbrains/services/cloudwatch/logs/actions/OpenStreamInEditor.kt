// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cloudwatch.logs.actions

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileTypes.PlainTextLanguage
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiFileFactory
import kotlinx.coroutines.withContext
import software.aws.toolkits.jetbrains.utils.notifyError
import software.aws.toolkits.resources.message
import kotlin.coroutines.ContinuationInterceptor

object OpenStreamInEditor {
    suspend fun open(project: Project, edt: ContinuationInterceptor, logStream: String, fileContent: String) {
        val factory = PsiFileFactory.getInstance(project)
        val file: PsiFile = factory.createFileFromText(
            logStream,
            PlainTextLanguage.INSTANCE,
            fileContent,
            true,
            false,
            true
        )
        withContext(edt) {
            file.virtualFile?.let {
                ApplicationManager.getApplication().runWriteAction {
                    it.isWritable = false
                }
                // set virtual file to read only
                FileEditorManager.getInstance(project).openFile(it, true, true).ifEmpty {
                    notifyError(message("cloudwatch.logs.open_in_editor_failed"))
                }
            }
        }
    }
}
