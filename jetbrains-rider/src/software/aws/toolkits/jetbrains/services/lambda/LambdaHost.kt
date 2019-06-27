// Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.lambda

import com.intellij.execution.Executor
import com.intellij.execution.ProgramRunnerUtil
import com.intellij.execution.RunManager
import com.intellij.execution.configurations.ConfigurationTypeUtil
import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.lang.LanguageUtil
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.LangDataKeys
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.project.Project
import com.intellij.psi.SmartPointerManager
import com.jetbrains.rdclient.util.idea.LifetimedProjectComponent
import com.jetbrains.rider.model.lambdaModel
import com.jetbrains.rider.projectView.solution
import software.aws.toolkits.jetbrains.core.credentials.ProjectAccountSettingsManager
import software.aws.toolkits.jetbrains.services.lambda.dotnet.DotNetLambdaHandlerResolver
import software.aws.toolkits.jetbrains.services.lambda.dotnet.element.RiderLambdaHandlerFakePsiElement
import software.aws.toolkits.jetbrains.services.lambda.execution.LambdaRunConfigurationType
import software.aws.toolkits.jetbrains.services.lambda.execution.local.LocalLambdaRunConfiguration
import software.aws.toolkits.jetbrains.services.lambda.execution.local.LocalLambdaRunConfigurationFactory
import software.aws.toolkits.jetbrains.services.lambda.upload.CreateLambdaFunction
import software.aws.toolkits.jetbrains.utils.RuntimeUtil

/**
 * Lambda Host class is used for communication with ReSharper backend through protocol
 * for all operation related to AWS Lambda.
 */
class LambdaHost(project: Project) : LifetimedProjectComponent(project) {

    val model = project.solution.lambdaModel

    init {
        initRunLambdaHandler()
        initDebugLambdaHandler()
        initCreateNewLambdaHandler()
    }

    private fun initRunLambdaHandler() =
        model.runLambda.advise(componentLifetime) { runLambdaRequest ->
            runConfiguration(
                methodName = runLambdaRequest.methodName,
                handler = runLambdaRequest.handler,
                executor = DefaultRunExecutor.getRunExecutorInstance()
            )
        }

    private fun initDebugLambdaHandler() =
        model.debugLambda.advise(componentLifetime) { debugLambdaRequest ->
            runConfiguration(
                methodName = debugLambdaRequest.methodName,
                handler = debugLambdaRequest.handler,
                executor = DefaultDebugExecutor.getDebugExecutorInstance()
            )
        }

    private fun initCreateNewLambdaHandler() =
        model.createNewLambda.advise(componentLifetime) { createLambdaRequest ->
            val handler = createLambdaRequest.handler

            val handlerResolver = DotNetLambdaHandlerResolver()
            val fieldId = handlerResolver.getFieldIdByHandlerName(project, handler)
            val psiElement = RiderLambdaHandlerFakePsiElement(project, handler, fieldId).navigationElement
            val smartPsiElementPointer = SmartPointerManager.createPointer(psiElement)

            val action = CreateLambdaFunction(
                handlerName = handler,
                elementPointer = smartPsiElementPointer,
                lambdaHandlerResolver = handlerResolver
            )

            val contextMap = mapOf(
                LangDataKeys.LANGUAGE.name to LanguageUtil.getRootLanguage(psiElement)
            )

            ActionUtil.invokeAction(
                action,
                SimpleDataContext.getSimpleContext(contextMap, SimpleDataContext.getProjectContext(project)),
                ActionPlaces.EDITOR_GUTTER_POPUP,
                null,
                null
            )
        }

    private fun runConfiguration(methodName: String, handler: String, executor: Executor) {
        val runManager = RunManager.getInstance(project)

        // Find configuration if exists
        val configurationType = ConfigurationTypeUtil.findConfigurationType(LambdaRunConfigurationType::class.java)
        val runConfigurations = runManager.getConfigurationsList(configurationType)

        var settings = runConfigurations.filterIsInstance<LocalLambdaRunConfiguration>().firstOrNull { configuration ->
            configuration.handler() == handler
        }?.let { configuration ->
            runManager.findSettings(configuration)
        }

        // Or generate a new one if configuration is missing
        if (settings == null) {
            val factory = LocalLambdaRunConfigurationFactory(configurationType)
            val template = runManager.getConfigurationTemplate(factory)

            val templateConfiguration = template.configuration as LocalLambdaRunConfiguration
            templateConfiguration.useHandler(RuntimeUtil.getCurrentDotNetCoreRuntime(), handler)

            val credentialProviderId = ProjectAccountSettingsManager.getInstance(project).activeCredentialProvider.id
            templateConfiguration.credentialProviderId(credentialProviderId)

            val configurationToAdd = factory.createConfiguration("[Local] $methodName", templateConfiguration)
            settings = runManager.createConfiguration(configurationToAdd, factory)

            runManager.addConfiguration(settings)
        }

        ProgramRunnerUtil.executeConfiguration(settings, executor)
    }
}
