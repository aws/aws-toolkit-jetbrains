// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.telemetry

import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import software.aws.toolkits.telemetry.SessionTelemetry
import java.util.concurrent.atomic.AtomicBoolean

class AwsToolkitStartupMetrics : ProjectActivity {
    companion object {
        private var runOnce = AtomicBoolean(false)
    }
    override suspend fun execute(project: Project) {
        if (runOnce.getAndSet(true)) return
        SessionTelemetry.start(project)
    }
}
