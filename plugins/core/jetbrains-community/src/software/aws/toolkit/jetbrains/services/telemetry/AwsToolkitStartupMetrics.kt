// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkit.jetbrains.services.telemetry

import com.intellij.ide.util.RunOnceUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import software.aws.toolkits.telemetry.SessionTelemetry

internal class AwsToolkitStartupMetrics : ProjectActivity {
    override suspend fun execute(project: Project) {
        RunOnceUtil.runOnceForApp(this::class.qualifiedName.toString()) {
            SessionTelemetry.start(project)
        }
    }
}
