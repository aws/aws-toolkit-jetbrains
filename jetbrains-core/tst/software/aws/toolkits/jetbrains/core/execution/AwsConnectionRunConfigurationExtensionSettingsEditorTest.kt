// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.core.execution

import com.intellij.execution.RunManager
import com.intellij.execution.application.ApplicationConfiguration
import com.intellij.execution.application.ApplicationConfigurationType
import com.intellij.testFramework.ProjectRule
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.ObjectAssert
import org.junit.Rule
import org.junit.Test

class AwsConnectionRunConfigurationExtensionSettingsEditorTest {

    @Rule
    @JvmField
    val projectRule = ProjectRule()

    @Test
    fun baseState() {
        val editor = AwsConnectionRunConfigurationExtensionSettingsEditor<ApplicationConfiguration>(projectRule.project)

        assertThat(editor.view.none.isSelected).isTrue()

        assertThat(editor.view.credentialProvider.isEnabled).isFalse()
        assertThat(editor.view.credentialProvider.itemCount).isZero() // We don't want to eagerly load for every RunConfiguration

        assertThat(editor.view.region.isEnabled).isFalse()
        assertThat(editor.view.region.itemCount).isZero() // We don't want to eagerly load for every RunConfiguration
    }

    @Test
    fun canRoundTripUseCurrentConnection() {
        val configuration = createConfiguration {
            useCurrentConnection = true
        }

        val editor = AwsConnectionRunConfigurationExtensionSettingsEditor<ApplicationConfiguration>(projectRule.project)

        editor.resetFrom(configuration)

        assertThat(editor.view.useCurrentConnection.isSelected).isTrue()

        assertThat(editor.view.credentialProvider.isEnabled).isFalse()
        assertThat(editor.view.credentialProvider.itemCount).isZero() // We don't want to eagerly load for every RunConfiguration
        assertThat(editor.view.region.isEnabled).isFalse()
        assertThat(editor.view.region.itemCount).isZero() // We don't want to eagerly load for every RunConfiguration

        assertThat(editor).isRoundTripped {
            useCurrentConnection = true
            region = null
            credential = null
        }
    }

    @Test
    fun canLoadSpecificManualSelection() {
        val configuration = createConfiguration {
            useCurrentConnection = false
            region = "us-east-1"
            credential = "DUMMY"
        }

        val editor = AwsConnectionRunConfigurationExtensionSettingsEditor<ApplicationConfiguration>(projectRule.project)

        editor.resetFrom(configuration)

        assertThat(editor.view.manuallyConfiguredConnection.isSelected).isTrue()
        assertThat(editor.view.region.isEnabled).isTrue()
        assertThat(editor.view.region.itemCount).isGreaterThan(0)
        assertThat(editor.view.credentialProvider.isEnabled).isTrue()
        assertThat(editor.view.credentialProvider.itemCount).isGreaterThan(0)

        assertThat(editor).isRoundTripped {
            useCurrentConnection = false
            region = "us-east-1"
            credential = "DUMMY"
        }
    }

    @Test
    fun canLoadNone() {
        val configuration = createConfiguration { }

        val editor = AwsConnectionRunConfigurationExtensionSettingsEditor<ApplicationConfiguration>(projectRule.project)

        editor.resetFrom(configuration)

        assertThat(editor.view.none.isSelected).isTrue()

        assertThat(editor.view.credentialProvider.isEnabled).isFalse()
        assertThat(editor.view.credentialProvider.itemCount).isZero() // We don't want to eagerly load for every RunConfiguration

        assertThat(editor.view.region.isEnabled).isFalse()
        assertThat(editor.view.region.itemCount).isZero() // We don't want to eagerly load for every RunConfiguration

        assertThat(editor).isRoundTripped {
            useCurrentConnection = false
            region = null
            credential = null
        }
    }

    @Test
    fun manualConnectionEnablesDropDowns() {
        val editor = AwsConnectionRunConfigurationExtensionSettingsEditor<ApplicationConfiguration>(projectRule.project)
        editor.view.manuallyConfiguredConnection.doClick()

        assertThat(editor.view.region.isEnabled).isTrue()
        assertThat(editor.view.credentialProvider.isEnabled).isTrue()
    }

    private fun createConfiguration(optionBuilder: AwsConnectionRunConfigurationExtensionOptions.() -> Unit): ApplicationConfiguration {
        val runManager = RunManager.getInstance(projectRule.project)
        val configuration = runManager.createConfiguration("test", ApplicationConfigurationType::class.java).configuration as ApplicationConfiguration
        configuration.putCopyableUserData(AWS_CONNECTION_RUN_CONFIGURATION_KEY, AwsConnectionRunConfigurationExtensionOptions().apply(optionBuilder))
        return configuration
    }

    private fun ApplicationConfiguration.extensionOptions() = getCopyableUserData(AWS_CONNECTION_RUN_CONFIGURATION_KEY)

    private fun ObjectAssert<AwsConnectionRunConfigurationExtensionSettingsEditor<ApplicationConfiguration>>.isRoundTripped(
        expected: AwsConnectionRunConfigurationExtensionOptions.() -> Unit
    ) {
        satisfies {
            val updatedConfiguration = createConfiguration { }
            it.applyTo(updatedConfiguration)
            assertThat(updatedConfiguration.extensionOptions()).isEqualToComparingFieldByField(AwsConnectionRunConfigurationExtensionOptions(expected))
        }
    }
}
