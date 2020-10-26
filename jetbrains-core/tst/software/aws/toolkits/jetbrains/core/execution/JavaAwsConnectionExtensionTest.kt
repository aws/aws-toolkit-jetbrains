// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.core.execution

import com.intellij.execution.RunManager
import com.intellij.execution.application.ApplicationConfiguration
import com.intellij.execution.application.ApplicationConfigurationType
import com.intellij.testFramework.ProjectRule
import com.nhaarman.mockitokotlin2.doAnswer
import com.nhaarman.mockitokotlin2.mock
import org.assertj.core.api.Assertions.assertThat
import org.jdom.Element
import org.jetbrains.plugins.gradle.service.execution.GradleRunConfiguration
import org.junit.Rule
import org.junit.Test
import software.aws.toolkits.jetbrains.settings.AwsSettingsRule

class JavaAwsConnectionExtensionTest {

    @Rule
    @JvmField
    val projectRule = ProjectRule()

    @Rule
    @JvmField
    val settingsRule = AwsSettingsRule()

    @Test
    fun `Round trip persistence`() {
        val runManager = RunManager.getInstance(projectRule.project)
        val configuration = runManager.createConfiguration("test", ApplicationConfigurationType::class.java).configuration as ApplicationConfiguration

        val data = AwsCredInjectionOptions {
            region = "abc123"
            credential = "mockCredential"
        }

        configuration.putCopyableUserData(AWS_CONNECTION_RUN_CONFIGURATION_KEY, data)
        configuration.mainClassName = "com.bla.Boop"

        val element = Element("bling")
        configuration.writeExternal(element)

        val deserialized = runManager.createConfiguration("re-read", ApplicationConfigurationType::class.java).configuration as ApplicationConfiguration
        deserialized.readExternal(element)

        assertThat(deserialized.mainClassName).isEqualTo("com.bla.Boop")
        assertThat(deserialized.getCopyableUserData(AWS_CONNECTION_RUN_CONFIGURATION_KEY)).isEqualToComparingFieldByField(data)
    }

    @Test
    fun `ignores gradle based run configs`() {
        val configuration = mock<GradleRunConfiguration>().apply {
            putCopyableUserData<AwsCredInjectionOptions>(AWS_CONNECTION_RUN_CONFIGURATION_KEY, AwsCredInjectionOptions {
                region = "abc123"
                credential = "mockCredential"
            })
        }

        assertThat(JavaAwsConnectionExtension().isApplicableFor(configuration)).isFalse()
    }

    @Test
    fun `Does not inject by default`() {
        val runManager = RunManager.getInstance(projectRule.project)
        val configuration = runManager.createConfiguration("test", ApplicationConfigurationType::class.java).configuration as ApplicationConfiguration
        assertThat(configuration.getCopyableUserData(AWS_CONNECTION_RUN_CONFIGURATION_KEY)).isNull()
    }

    @Test
    fun `Inject injects environment variables`() {
        val runManager = RunManager.getInstance(projectRule.project)
        val configuration = runManager.createConfiguration("test", ApplicationConfigurationType::class.java).configuration as ApplicationConfiguration
        val data = AwsCredInjectionOptions {
            region = "abc123"
            credential = "mockCredential"
        }
        configuration.putCopyableUserData(AWS_CONNECTION_RUN_CONFIGURATION_KEY, data)

        val extension = JavaAwsConnectionExtension()
        val map = mutableMapOf<String, String>()
        extension.updateJavaParameters(configuration, mock { on { env } doAnswer { map } }, null)
        assertThat(map).hasSize(6)
        listOf(
            "AWS_ACCESS_KEY",
            "AWS_ACCESS_KEY_ID",
            "AWS_REGION",
            "AWS_DEFAULT_REGION",
            "AWS_SECRET_KEY",
            "AWS_SECRET_ACCESS_KEY"
        ).forEach { assertThat(map[it]).isNotNull() }
    }
}
