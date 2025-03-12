// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonq

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.util.Alarm
import software.aws.toolkits.telemetry.MetricResult
import software.aws.toolkits.telemetry.Telemetry

@Service(Service.Level.PROJECT)
class LoadModuleCompletion(project: Project?) : Disposable{

    private val alarm = Alarm(Alarm.ThreadToUse.POOLED_THREAD, this)
    private var initializeLoad = true

    fun start(moduleName: String) {
        alarm.addRequest(
            {
                if(!initializeLoad) {
                    emitMetric(moduleName)

                }
                initializeLoad = false


            },
            10000
        )
    }

    private fun emitMetric(moduleName: String) {
        Telemetry.toolkit.didLoadModule.use {
            it.module(moduleName)
            it.result(MetricResult.Failed)
        }
        initializeLoad = true
    }

    fun resetTimer() {
        alarm.cancelAllRequests()

    }

    companion object {
        fun getInstance(project: Project?) = project?.service<LoadModuleCompletion>()
    }

    override fun dispose() {}
}
