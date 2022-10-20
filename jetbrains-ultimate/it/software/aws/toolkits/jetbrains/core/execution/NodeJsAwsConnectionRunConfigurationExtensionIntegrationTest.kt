// Copyright 2022 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.core.execution

import com.intellij.execution.ExecutorRegistry
import com.intellij.execution.RunManager
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.openapi.module.ModuleType
import com.intellij.testFramework.PsiTestUtil
import com.intellij.util.ReflectionUtil
import com.jetbrains.nodejs.run.NodeJsRunConfiguration
import com.jetbrains.nodejs.run.NodeJsRunConfigurationType
import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Rule
import org.junit.Test
import software.aws.toolkits.jetbrains.core.credentials.MockCredentialManagerRule
import software.aws.toolkits.jetbrains.core.region.MockRegionProviderRule
import software.aws.toolkits.jetbrains.utils.executeRunConfigurationAndWait
import software.aws.toolkits.jetbrains.utils.rules.ExperimentRule
import software.aws.toolkits.jetbrains.utils.rules.HeavyNodeJsCodeInsightTestFixtureRule
import java.util.concurrent.DelayQueue
import java.util.concurrent.TimeUnit
import javax.swing.Timer
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

    @After
    fun tearDown() {
        checkJavaSwingTimersAreDisposed()
    }

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
        val runConfigurationType = runManager.createConfiguration("test", NodeJsRunConfigurationType::class.java)
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

    // Adapted from https://github.com/JetBrains/intellij-community/blob/2926d8e27e2e0d8120a63089d1173b7e461e3dd1/platform/testFramework/src/com/intellij/testFramework/TestApplicationManager.kt#L338
    // See https://intellij-support.jetbrains.com/hc/en-us/community/posts/360006918780-Tests-Fail-due-to-Java-Swing-Timers-Not-Disposed
    // Seems to have been removed in 223: https://github.com/JetBrains/intellij-community/blob/223.7126/platform/testFramework/src/com/intellij/testFramework/TestApplicationManager.kt
    private fun checkJavaSwingTimersAreDisposed() {
        val timerQueueClass = Class.forName("javax.swing.TimerQueue")
        val sharedInstance = timerQueueClass.getMethod("sharedInstance")
        sharedInstance.isAccessible = true
        val timerQueue = sharedInstance.invoke(null)
        val delayQueue = ReflectionUtil.getField(timerQueueClass, timerQueue, DelayQueue::class.java, "queue")
        while (true) {
            val timer = delayQueue.peek() ?: return
            val delay = timer.getDelay(TimeUnit.MILLISECONDS)
            val getTimer = ReflectionUtil.getDeclaredMethod(timer.javaClass, "getTimer")!!
            val swingTimer = getTimer.invoke(timer) as Timer
            println("Not disposed javax.swing.Timer: (listeners: ${listOf(*swingTimer.actionListeners)}) (delayed for ${delay}ms)")
            swingTimer.stop()
        }
    }
}
