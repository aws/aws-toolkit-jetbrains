// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cloudwatch.logs.actions

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileTypes.PlainTextLanguage
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiFileFactory
import com.intellij.util.ui.ListTableModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import software.amazon.awssdk.services.cloudwatchlogs.model.OutputLogEvent
import software.aws.toolkits.jetbrains.utils.getCoroutineUiContext
import software.aws.toolkits.jetbrains.utils.notifyError

class OpenCurrentInEditor(
    private val project: Project,
    private val logStream: String,
    private val logsTableModel: ListTableModel<OutputLogEvent>
) :
    AnAction("Open in editor <LOCALIZE>", null, AllIcons.Actions.Menu_open), CoroutineScope by GlobalScope, DumbAware {
    private val edt = getCoroutineUiContext(ModalityState.defaultModalityState())

    override fun actionPerformed(e: AnActionEvent) {
        launch {
            actionPerformedSuspend(e)
        }
    }

    private suspend fun actionPerformedSuspend(e: AnActionEvent) {
        val factory = PsiFileFactory.getInstance(project)
        val file: PsiFile = factory.createFileFromText(
            logStream,
            PlainTextLanguage.INSTANCE,
            logsTableModel.items.joinToString("") { if (it.message().endsWith("\n")) it.message() else "${it.message()}\n" },
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
                    notifyError("open in logs failed <localize>")
                }
            }
        }
    }
}
