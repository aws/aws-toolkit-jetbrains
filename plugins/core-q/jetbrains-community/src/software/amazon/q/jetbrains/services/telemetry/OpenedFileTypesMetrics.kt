// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.amazon.q.jetbrains.services.telemetry

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.Alarm
import org.jetbrains.annotations.TestOnly
import software.aws.toolkits.telemetry.IdeTelemetry

class OpenedFileTypesMetricsListener : FileEditorManagerListener {
    override fun fileOpened(source: FileEditorManager, file: VirtualFile) {
        val extension = file.extension ?: return
        source.project.service<OpenedFileTypesMetricsService>().addToExistingTelemetryBatch(extension)
    }
}

@Service(Service.Level.PROJECT)
class OpenedFileTypesMetricsService : Disposable {
    private val currentOpenedFileTypes = mutableSetOf<String>()
    private val alarm = Alarm(Alarm.ThreadToUse.POOLED_THREAD, this)
    override fun dispose() {}

    init {
        scheduleNextMetricEvent()
    }

    private fun scheduleNextMetricEvent() {
        alarm.addRequest(this::emitFileTypeMetric, INTERVAL_BETWEEN_METRICS)
    }

    @Synchronized
    fun emitFileTypeMetric() {
        currentOpenedFileTypes.forEach {
            emitMetric(it)
        }
        currentOpenedFileTypes.clear()
        if (!ApplicationManager.getApplication().isUnitTestMode) {
            scheduleNextMetricEvent()
        }
    }

    @TestOnly
    fun getOpenedFileTypes(): Set<String> = currentOpenedFileTypes

    @Synchronized
    fun addToExistingTelemetryBatch(fileExt: String) {
        if (fileExt in ALLOWED_CODE_EXTENSIONS) {
            val extension = ".$fileExt"
            currentOpenedFileTypes.add(extension)
        }
    }

    private fun emitMetric(openFileExtension: String) =
        IdeTelemetry.editCodeFile(project = null, filenameExt = openFileExtension)

    companion object {
        private const val INTERVAL_BETWEEN_METRICS = 30 * 60 * 1000
    }
}
