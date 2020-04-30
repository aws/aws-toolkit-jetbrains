// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.core.execution

import com.intellij.execution.RunManager
import com.intellij.execution.application.ApplicationConfiguration
import com.intellij.execution.application.ApplicationConfigurationType
import com.intellij.testFramework.ProjectRule
import org.assertj.core.api.Assertions.assertThat
import org.jdom.Element
import org.junit.Rule
import org.junit.Test

class JavaAwsConnectionRunConfigurationExtensionTest {

    @Rule
    @JvmField
    val projectRule = ProjectRule()

    @Test
    fun canRoundTripPersistence() {
        val runManager = RunManager.getInstance(projectRule.project)
        val configuration = runManager.createConfiguration("test", ApplicationConfigurationType::class.java).configuration as ApplicationConfiguration

        val data = AwsConnectionRunConfigurationExtensionOptions().also {
            it.region = "abc123"
            it.credential = "mockCredential"
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
}
