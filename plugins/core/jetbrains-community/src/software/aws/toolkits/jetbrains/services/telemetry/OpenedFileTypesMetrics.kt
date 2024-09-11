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
import software.aws.toolkits.jetbrains.utils.notifyInfo
import software.aws.toolkits.telemetry.IdeTelemetry

class OpenedFileTypesMetrics : ProjectActivity, Disposable {
    private val currentOpenedFileTypes = mutableSetOf<String>()
    private val alarm = Alarm(Alarm.ThreadToUse.POOLED_THREAD, this)
    override suspend fun execute(project: Project) {
        // add already open file extensions
        FileEditorManager.getInstance(project).openFiles.forEach {
            val extension = it.extension ?: return@forEach
            addToExistingTelemetryBatch(extension)
        }

        // add newly opened file extensions
        project.messageBus.connect(this).subscribe(
            FileEditorManagerListener.FILE_EDITOR_MANAGER,
            object : FileEditorManagerListener {
                override fun fileOpened(source: FileEditorManager, file: VirtualFile) {
                    val extension = file.extension ?: return
                    addToExistingTelemetryBatch(extension)
                }
            }
        )
        scheduleNextMetricEvent()
    }

    private fun addToExistingTelemetryBatch(fileExt: String) {
        // val extension = ".$fileExt"
        notifyInfo("ex")
        if (fileExt in ALLOWED_CODE_EXTENSIONS) {
            currentOpenedFileTypes.add(fileExt)
        }
    }

    fun scheduleNextMetricEvent() {
        alarm.addRequest(this::emitFileTypeMetric, INTERVAL_BETWEEN_METRICS)
    }

    override fun dispose() {}

    fun emitFileTypeMetric() {
        currentOpenedFileTypes.forEach {
            emitMetric(it)
        }
        currentOpenedFileTypes.clear()
        if (!ApplicationManager.getApplication().isUnitTestMode) {
            scheduleNextMetricEvent()
        }
    }

    fun emitMetric(openFileExtension: String) {
        IdeTelemetry.editCodeFile(project = null, filenameExt = openFileExtension)
    }

    companion object {
        private const val INTERVAL_BETWEEN_METRICS = 30 * 60 * 1000
    }
}
