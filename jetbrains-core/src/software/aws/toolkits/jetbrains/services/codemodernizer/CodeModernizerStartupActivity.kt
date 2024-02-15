// Copyright 2023 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.codemodernizer

import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity
import software.aws.toolkits.jetbrains.services.codemodernizer.state.CodeTransformTelemetryState
import software.aws.toolkits.telemetry.CodeTransformPreValidationError
import software.aws.toolkits.telemetry.CodetransformTelemetry
import software.aws.toolkits.telemetry.Result

class CodeModernizerStartupActivity : StartupActivity.DumbAware {

    /**
     * Will be run on startup of the IDE
     * Prompts users of jobs that finished while IDE was closed.
     */
    override fun runActivity(project: Project) {
        if (!isCodeModernizerAvailable(project)) return
        // Do quick validation and log users project details on startup.
        val codeModernizerManager = CodeModernizerManager.getInstance(project)
        val validationResult = codeModernizerManager.validate(project)
        CodetransformTelemetry.projectDetails(
            codeTransformSessionId = CodeTransformTelemetryState.instance.getSessionId(),
            result = if (!validationResult.valid) Result.Failed else Result.Succeeded,
            reason = validationResult.invalidTelemetryReason.additonalInfo,
            codeTransformPreValidationError = validationResult.invalidTelemetryReason.category ?: CodeTransformPreValidationError.Unknown,
            codeTransformLocalJavaVersion = project.tryGetJdk().toString()
        )
        // Post project validation try to re-start the job
        CodeModernizerManager.getInstance(project).tryResumeJob()
    }
}
