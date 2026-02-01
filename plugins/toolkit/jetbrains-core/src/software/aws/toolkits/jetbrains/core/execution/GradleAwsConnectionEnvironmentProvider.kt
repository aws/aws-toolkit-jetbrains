// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.core.execution

import com.intellij.execution.CommonJavaRunConfigurationParameters
import com.intellij.execution.Executor
import com.intellij.execution.configurations.RunConfigurationBase
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.project.Project
import com.intellij.task.ExecuteRunConfigurationTask
import org.jetbrains.plugins.gradle.execution.build.GradleExecutionEnvironmentProvider
import org.jetbrains.plugins.gradle.service.execution.GradleRunConfiguration
import software.aws.toolkits.jetbrains.core.experiments.isEnabled

/**
 * This provider intercepts Gradle executions that originate from Java/Kotlin run configurations
 * and transfers AWS connection settings to the Gradle run configuration.
 *
 * When running a Java/Kotlin app via Gradle in IntelliJ 2025.2+, the IDE:
 * 1. Creates a JavaRunConfiguration/KotlinRunConfiguration (where AWS settings are configured)
 * 2. Internally creates a separate GradleRunConfiguration to execute via Gradle
 *
 * Without this provider, AWS connection settings from the original configuration would not
 * be transferred to the Gradle execution.
 *
 * This provider is registered with order="first" so it gets called before other providers,
 * allowing it to intercept the execution and delegate to other providers while adding
 * AWS environment variables to the resulting GradleRunConfiguration.
 */
class GradleAwsConnectionEnvironmentProvider : GradleExecutionEnvironmentProvider {
    private val delegate = AwsConnectionRunConfigurationExtension<RunConfigurationBase<*>>()

    override fun isApplicable(task: ExecuteRunConfigurationTask): Boolean {
        if (!JavaAwsConnectionExperiment.isEnabled()) {
            return false
        }

        val runProfile = task.runProfile

        // Check if the run profile is a RunConfigurationBase with AWS connection settings
        // and implements CommonJavaRunConfigurationParameters
        if (runProfile is RunConfigurationBase<*> && runProfile is CommonJavaRunConfigurationParameters) {
            val awsSettings = runProfile.getCopyableUserData(AWS_CONNECTION_RUN_CONFIGURATION_KEY)
            return awsSettings != null && awsSettings != AwsCredentialInjectionOptions.DEFAULT_OPTIONS
        }

        return false
    }

    override fun createExecutionEnvironment(
        project: Project,
        task: ExecuteRunConfigurationTask,
        executor: Executor,
    ): ExecutionEnvironment? {
        // Delegate to other providers to create the actual execution environment
        val environment = GradleExecutionEnvironmentProvider.EP_NAME.extensionList
            .asSequence()
            .filter { it !== this }
            .mapNotNull { provider ->
                if (provider.isApplicable(task)) {
                    provider.createExecutionEnvironment(project, task, executor)
                } else {
                    null
                }
            }
            .firstOrNull()

        // If we got an environment with a GradleRunConfiguration, apply AWS settings
        if (environment != null && environment.runProfile is GradleRunConfiguration) {
            val runProfile = task.runProfile
            if (runProfile is RunConfigurationBase<*>) {
                applyAwsConnection(runProfile, environment.runProfile as GradleRunConfiguration)
            }
        }

        return environment
    }

    private fun applyAwsConnection(
        sourceConfig: RunConfigurationBase<*>,
        targetConfig: GradleRunConfiguration,
    ) {
        val environment = targetConfig.settings.env.toMutableMap()
        delegate.addEnvironmentVariables(sourceConfig, environment)
        targetConfig.settings.env = environment
    }
}
