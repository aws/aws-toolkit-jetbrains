// Copyright 2022 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.core.execution

import com.intellij.execution.ExecutorRegistry
import com.intellij.execution.RunManager
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.openapi.module.ModuleType
import com.intellij.testFramework.PsiTestUtil
import com.jetbrains.nodejs.run.NodeJsRunConfiguration
import com.jetbrains.nodejs.run.NodeJsRunConfigurationType
import org.assertj.core.api.Assertions.assertThat
import org.junit.Rule
import org.junit.Test
import software.aws.toolkits.jetbrains.core.credentials.MockCredentialManagerRule
import software.aws.toolkits.jetbrains.core.region.MockRegionProviderRule
import software.aws.toolkits.jetbrains.utils.executeRunConfigurationAndWait
import software.aws.toolkits.jetbrains.utils.rules.ExperimentRule
import software.aws.toolkits.jetbrains.utils.rules.HeavyNodeJsCodeInsightTestFixtureRule
import kotlin.test.assertNotNull

class NodeJsAwsConnectionRunConfigurationExtensionIntegrationTest {

    @Rule
    @JvmField
    val projectRule = HeavyNodeJsCodeInsightTestFixtureRule()

    @Rule
    @JvmField
    val credentialsManager = MockCredentialManagerRule()

    @Rule
    @JvmField
    val regionProviderRule = MockRegionProviderRule()

    @Rule
    @JvmField
    val experiment = ExperimentRule(NodeJsAwsConnectionExperiment)

    @Test
    fun environmentVariablesAreInjected() {
        val fixture = projectRule.fixture

        PsiTestUtil.addModule(projectRule.project, ModuleType.EMPTY, "main", fixture.tempDirFixture.findOrCreateDir("."))

        // language=JS
        val jsFunction = """
            console.log(process.env["AWS_REGION"])
        """.trimIndent()

        val psiFile = fixture.addFileToProject("test/app.js", jsFunction)

        val runManager = RunManager.getInstance(projectRule.project)
        val runConfigurationType = runManager.createConfiguration("", NodeJsRunConfigurationType::class.java)
        val runConfiguration = runConfigurationType.configuration as NodeJsRunConfiguration

        val mockRegion = regionProviderRule.createAwsRegion()
        val mockCredentials = credentialsManager.createCredentialProvider()

        runConfiguration.mainScriptFilePath = psiFile.virtualFile.canonicalPath
        runConfiguration.putCopyableUserData(
            AWS_CONNECTION_RUN_CONFIGURATION_KEY,
            AwsCredentialInjectionOptions {
                region = mockRegion.id
                credential = mockCredentials.id
            }
        )

        val executor = ExecutorRegistry.getInstance().getExecutorById(DefaultRunExecutor.EXECUTOR_ID)
        assertNotNull(executor)

        assertThat(executeRunConfigurationAndWait(runConfiguration).stdout).isEqualToIgnoringWhitespace(mockRegion.id)
    }
}
