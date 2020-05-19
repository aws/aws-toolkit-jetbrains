// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.core.execution

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.configurations.RunConfigurationBase
import com.intellij.openapi.util.Key
import com.intellij.util.xmlb.XmlSerializer
import org.jdom.Element
import software.aws.toolkits.core.utils.error
import software.aws.toolkits.core.utils.getLogger
import software.aws.toolkits.jetbrains.core.credentials.ConnectionSettings
import software.aws.toolkits.jetbrains.core.credentials.CredentialManager
import software.aws.toolkits.jetbrains.core.credentials.activeConnection
import software.aws.toolkits.jetbrains.core.credentials.toEnvironmentVariables
import software.aws.toolkits.jetbrains.core.region.AwsRegionProvider
import software.aws.toolkits.resources.message
import software.aws.toolkits.telemetry.AwsTelemetry
import software.aws.toolkits.telemetry.Result.FAILED
import software.aws.toolkits.telemetry.Result.SUCCEEDED

class AwsConnectionRunConfigurationExtension<T : RunConfigurationBase<*>> {
    private val regionProvider = AwsRegionProvider.getInstance()
    private val credentialManager = CredentialManager.getInstance()

    fun addEnvironmentVariables(configuration: T, environment: MutableMap<String, String>) {
        val credentialConfiguration = configuration.getCopyableUserData(AWS_CONNECTION_RUN_CONFIGURATION_KEY) ?: return

        try {
            val connection = if (credentialConfiguration.useCurrentConnection) {
                configuration.project.activeConnection() ?: throw RuntimeException(message("configure.toolkit"))
            } else {
                val region = credentialConfiguration.region?.let {
                    regionProvider.allRegions()[it]
                } ?: throw IllegalStateException(message("configure.validate.no_region_specified"))

                val credentialProviderId = credentialConfiguration.credential ?: throw IllegalStateException(message("aws.notification.credentials_missing"))

                val credentialProvider = credentialManager.getCredentialIdentifierById(credentialProviderId)?.let {
                    credentialManager.getAwsCredentialProvider(it, region)
                } ?: throw RuntimeException(message("aws.notification.credentials_missing"))

                ConnectionSettings(credentialProvider, region)
            }

            connection.toEnvironmentVariables().forEach { (key, value) -> environment[key] = value }
            AwsTelemetry.injectCredentials(configuration.project, result = SUCCEEDED)
        } catch (e: Exception) {
            AwsTelemetry.injectCredentials(configuration.project, result = FAILED)
            LOG.error(e) { message("run_configuration_extension.inject_aws_connection_exception") }
        }
    }

    fun readExternal(runConfiguration: T, element: Element) {
        runConfiguration.putCopyableUserData(
            AWS_CONNECTION_RUN_CONFIGURATION_KEY,
            XmlSerializer.deserialize(
                element,
                AwsConnectionRunConfigurationExtensionOptions::class.java
            )
        )
    }

    fun writeExternal(runConfiguration: T, element: Element) {
        runConfiguration.getCopyableUserData(AWS_CONNECTION_RUN_CONFIGURATION_KEY)?.let {
            XmlSerializer.serializeInto(it, element)
        }
    }

    private companion object {
        val LOG = getLogger<AwsConnectionRunConfigurationExtension<*>>()
    }
}

fun <T : RunConfigurationBase<*>> AwsConnectionRunConfigurationExtension<T>.addEnvironmentVariables(
    configuration: T,
    cmdLine: GeneralCommandLine
) = addEnvironmentVariables(configuration, cmdLine.environment)

fun <T : RunConfigurationBase<*>?> connectionSettingsEditor(configuration: T): AwsConnectionRunConfigurationExtensionSettingsEditor<T>? =
    configuration?.getProject()?.let { AwsConnectionRunConfigurationExtensionSettingsEditor(it) }

val AWS_CONNECTION_RUN_CONFIGURATION_KEY =
    Key.create<AwsConnectionRunConfigurationExtensionOptions>(
        "aws.toolkit.runConfigurationConnection"
    )

class AwsConnectionRunConfigurationExtensionOptions {
    var useCurrentConnection: Boolean = false
    var region: String? = null
    var credential: String? = null

    companion object {
        operator fun invoke(block: AwsConnectionRunConfigurationExtensionOptions.() -> Unit) =
            AwsConnectionRunConfigurationExtensionOptions().apply(block)
    }
}
