// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.telemetry

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.Alarm
import kotlinx.coroutines.coroutineScope
import software.aws.toolkits.telemetry.IdeTelemetry

class OpenedFileTypesMetrics : ProjectActivity, Disposable {
    private val currentOpenedFileTypes = mutableSetOf<String>()
    private val alarm = Alarm(Alarm.ThreadToUse.POOLED_THREAD, this)
    override suspend fun execute(project: Project) {
        // add already open file extensions
        FileEditorManager.getInstance(project).openFiles.forEach {
            it.extension?.let { openFileExtension -> currentOpenedFileTypes.add(openFileExtension) }
        }

        // add newly opened file extensions
        project.messageBus.connect(this).subscribe(
            FileEditorManagerListener.FILE_EDITOR_MANAGER,
            object : FileEditorManagerListener {
                override fun fileOpened(source: FileEditorManager, file: VirtualFile) {
                    file.extension?.let { currentOpenedFileTypes.add(it) }
                }
            }
        )
        scheduleNextMetricEvent()
    }

    private fun scheduleNextMetricEvent() {
        alarm.addRequest(this::emitFileTypeMetric, INTERVAL_BETWEEN_METRICS)
    }

    override fun dispose() {}

    private fun emitFileTypeMetric() {
        ApplicationManager.getApplication().executeOnPooledThread{
            currentOpenedFileTypes.forEach {
                IdeTelemetry.editCodeFile(project = null, filenameExt = it)
            }
            currentOpenedFileTypes.clear()
            scheduleNextMetricEvent()
        }


    }

    companion object {
        const val INTERVAL_BETWEEN_METRICS = 60 * 60 * 1000
    }
}
