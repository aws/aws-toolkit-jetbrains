// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.core.execution

import com.intellij.execution.RunManager
import com.intellij.execution.application.ApplicationConfiguration
import com.intellij.execution.application.ApplicationConfigurationType
import com.intellij.task.ExecuteRunConfigurationTask
import com.intellij.testFramework.ProjectRule
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.plugins.gradle.service.execution.GradleRunConfiguration
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.aws.toolkits.jetbrains.core.credentials.MockCredentialsManager
import software.aws.toolkits.jetbrains.core.region.MockRegionProviderRule
import software.aws.toolkits.jetbrains.settings.AwsSettingsRule
import software.aws.toolkits.jetbrains.utils.rules.ExperimentRule

class GradleAwsConnectionEnvironmentProviderTest {

    @Rule
    @JvmField
    val projectRule = ProjectRule()

    @Rule
    @JvmField
    val settingsRule = AwsSettingsRule()

    @Rule
    @JvmField
    val regionProvider = MockRegionProviderRule()

    @Rule
    @JvmField
    val registryRule = ExperimentRule(JavaAwsConnectionExperiment)

    private val mockCreds = AwsBasicCredentials.create("Access", "ItsASecret")

    @Before
    fun setUp() {
        MockCredentialsManager.getInstance().addCredentials("MockCredentials", mockCreds)
    }

    @Test
    fun `isApplicable returns true for ApplicationConfiguration with AWS settings`() {
        val runManager = RunManager.getInstance(projectRule.project)
        val configuration = runManager.createConfiguration("test", ApplicationConfigurationType::class.java).configuration as ApplicationConfiguration

        val data = AwsCredentialInjectionOptions {
            region = regionProvider.defaultRegion().id
            credential = "MockCredentials"
        }
        configuration.putCopyableUserData(AWS_CONNECTION_RUN_CONFIGURATION_KEY, data)

        val task = mock<ExecuteRunConfigurationTask>()
        whenever(task.runProfile).thenReturn(configuration)

        val provider = GradleAwsConnectionEnvironmentProvider()
        assertThat(provider.isApplicable(task)).isTrue()
    }

    @Test
    fun `isApplicable returns false for ApplicationConfiguration without AWS settings`() {
        val runManager = RunManager.getInstance(projectRule.project)
        val configuration = runManager.createConfiguration("test", ApplicationConfigurationType::class.java).configuration as ApplicationConfiguration

        val task = mock<ExecuteRunConfigurationTask>()
        whenever(task.runProfile).thenReturn(configuration)

        val provider = GradleAwsConnectionEnvironmentProvider()
        assertThat(provider.isApplicable(task)).isFalse()
    }

    @Test
    fun `isApplicable returns false for ApplicationConfiguration with default AWS options`() {
        val runManager = RunManager.getInstance(projectRule.project)
        val configuration = runManager.createConfiguration("test", ApplicationConfigurationType::class.java).configuration as ApplicationConfiguration
        configuration.putCopyableUserData(AWS_CONNECTION_RUN_CONFIGURATION_KEY, AwsCredentialInjectionOptions.DEFAULT_OPTIONS)

        val task = mock<ExecuteRunConfigurationTask>()
        whenever(task.runProfile).thenReturn(configuration)

        val provider = GradleAwsConnectionEnvironmentProvider()
        assertThat(provider.isApplicable(task)).isFalse()
    }

    @Test
    fun `isApplicable returns false for GradleRunConfiguration`() {
        val configuration = mock<GradleRunConfiguration>()

        val task = mock<ExecuteRunConfigurationTask>()
        whenever(task.runProfile).thenReturn(configuration)

        val provider = GradleAwsConnectionEnvironmentProvider()
        assertThat(provider.isApplicable(task)).isFalse()
    }
}
