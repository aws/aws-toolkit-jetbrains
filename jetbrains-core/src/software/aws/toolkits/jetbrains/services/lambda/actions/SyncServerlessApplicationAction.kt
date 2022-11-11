// Copyright 2022 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.lambda.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import icons.AwsIcons
import software.aws.toolkits.jetbrains.core.experiments.isEnabled
import software.aws.toolkits.jetbrains.services.lambda.LambdaHandlerResolver
import software.aws.toolkits.jetbrains.services.lambda.SyncServerlessApplicationExperiment
import software.aws.toolkits.jetbrains.services.lambda.sam.getSamTemplateFile

class SyncServerlessApplicationAction : AnAction({ "Sync Serverless Application" }, AwsIcons.Resources.SERVERLESS_APP) {
    override fun actionPerformed(e: AnActionEvent) {
        SyncServerlessAppAction().actionPerformed(e)
    }

    override fun update(e: AnActionEvent) {
        super.update(e)
        e.presentation.isEnabledAndVisible = getSamTemplateFile(e) != null &&
            SyncServerlessApplicationExperiment.isEnabled() &&
            LambdaHandlerResolver.supportedRuntimeGroups().isNotEmpty()
    }
}
