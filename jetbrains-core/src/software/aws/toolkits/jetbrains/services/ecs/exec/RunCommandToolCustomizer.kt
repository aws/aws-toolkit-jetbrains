// Copyright 2021 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.ecs.exec

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.tools.ToolsCustomizer
import software.aws.toolkits.jetbrains.core.credentials.AwsConnectionManager
import software.aws.toolkits.jetbrains.core.credentials.toEnvironmentVariables

class RunCommandToolCustomizer : ToolsCustomizer() {
    override fun customizeCommandLine(dataContext: DataContext, commandLine: GeneralCommandLine): GeneralCommandLine {
        val connectionSettings = dataContext.getData(CommonDataKeys.PROJECT)?.let { AwsConnectionManager.getInstance(it).connectionSettings() }
        if (connectionSettings != null) {
            val environmentVariables = connectionSettings.region.toEnvironmentVariables() +
                connectionSettings.credentials.resolveCredentials().toEnvironmentVariables()
            commandLine.withEnvironment(environmentVariables)
        }
        return commandLine
    }
}
