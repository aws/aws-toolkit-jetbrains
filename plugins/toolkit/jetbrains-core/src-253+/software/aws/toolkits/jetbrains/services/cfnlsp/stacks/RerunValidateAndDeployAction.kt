// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cfnlsp.stacks

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import software.aws.toolkits.jetbrains.utils.notifyError
import java.util.UUID

internal class RerunValidateAndDeployAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val lastParams = LastValidationService.getInstance(project).lastParams

        if (lastParams == null) {
            notifyError("CloudFormation", "No previous validation to rerun", project = project)
            return
        }

        ValidationWorkflow(project).validate(lastParams.copy(id = UUID.randomUUID().toString()))
    }

    override fun update(e: AnActionEvent) {
        val project = e.project ?: return
        e.presentation.isEnabled = LastValidationService.getInstance(project).lastParams != null
    }
}
