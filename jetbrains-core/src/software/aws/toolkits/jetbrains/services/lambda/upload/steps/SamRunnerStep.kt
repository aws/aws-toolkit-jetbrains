// Copyright 2021 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.lambda.upload.steps

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.net.NetUtils
import software.aws.toolkits.core.utils.AttributeBagKey
import software.aws.toolkits.jetbrains.core.credentials.toEnvironmentVariables
import software.aws.toolkits.jetbrains.services.lambda.execution.sam.ImageTemplateRunSettings
import software.aws.toolkits.jetbrains.services.lambda.execution.sam.LocalLambdaRunSettings
import software.aws.toolkits.jetbrains.services.lambda.execution.sam.RuntimeDebugSupport
import software.aws.toolkits.jetbrains.services.lambda.execution.sam.TemplateSettings
import software.aws.toolkits.jetbrains.services.lambda.execution.sam.ZipSettings
import software.aws.toolkits.jetbrains.utils.execution.steps.Context
import software.aws.toolkits.resources.message

class SamRunnerStep(val environment: ExecutionEnvironment, val settings: LocalLambdaRunSettings, val debug: Boolean) : SamCliStep() {
    override val stepName: String = message("lambda.debug.step.start_sam")
    override fun constructCommandLine(context: Context): GeneralCommandLine {
        val builtLambda = context.getRequiredAttribute(BuildLambda.BUILT_LAMBDA)
        val totalEnvVars = settings.environmentVariables +
            settings.connection.credentials.resolveCredentials().toEnvironmentVariables() +
            settings.connection.region.toEnvironmentVariables()

        val commandLine = getCli()
            .withParameters("local")
            .withParameters("invoke")
            .apply {
                if (settings is TemplateSettings) {
                    withParameters(settings.logicalId)
                }
            }
            .withParameters("--template")
            .withParameters(builtLambda.templateLocation.toString())
            .withParameters("--event")
            .withParameters(createEventFile())
            .withEnvironment(totalEnvVars)
            .withEnvironment("PYTHONUNBUFFERED", "1") // Force SAM to not buffer stdout/stderr so it gets shown in IDE

        if (debug) {
            val debugExtension = resolveDebuggerSupport(settings)
            val debugPorts = NetUtils.findAvailableSocketPorts(debugExtension.numberOfDebugPorts()).toList()

            commandLine.addParameters(debugExtension.samArguments(debugPorts))
            debugPorts.forEach {
                commandLine.withParameters("--debug-port").withParameters(it.toString())
            }

            context.putAttribute(DEBUG_PORTS, debugPorts)
        }

        val samOptions = settings.samOptions
        if (samOptions.skipImagePull) {
            commandLine.withParameters("--skip-pull-image")
        }

        samOptions.dockerNetwork?.let {
            if (it.isNotBlank()) {
                commandLine.withParameters("--docker-network")
                    .withParameters(it.trim())
            }
        }

        samOptions.additionalLocalArgs?.let {
            if (it.isNotBlank()) {
                commandLine.withParameters(*it.split(" ").toTypedArray())
            }
        }

        return commandLine
    }

    private fun resolveDebuggerSupport(settings: LocalLambdaRunSettings) = when (settings) {
        is ImageTemplateRunSettings -> settings.imageDebugger
        is ZipSettings -> RuntimeDebugSupport.getInstance(settings.runtimeGroup)
        else -> throw IllegalStateException("Can't find debugger support for $settings")
    }

    private fun createEventFile(): String {
        val eventFile = FileUtil.createTempFile("${environment.runProfile.name}-event", ".json", true)
        eventFile.writeText(settings.input)
        return eventFile.absolutePath
    }

    companion object {
        val DEBUG_PORTS = AttributeBagKey.create<List<Int>>("DEBUG_PORTS")
    }
}
