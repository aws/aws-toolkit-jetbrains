// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.uitests.docTests.createReadmeTests

import com.intellij.driver.sdk.waitForProjectOpen
import com.intellij.ide.starter.ci.CIServer
import com.intellij.ide.starter.config.ConfigurationStorage
import com.intellij.ide.starter.di.di
import com.intellij.ide.starter.driver.engine.runIdeWithDriver
import com.intellij.ide.starter.ide.IdeProductProvider
import com.intellij.ide.starter.junit5.hyphenateWithClass
import com.intellij.ide.starter.models.TestCase
import com.intellij.ide.starter.project.LocalProjectInfo
import com.intellij.ide.starter.runner.CurrentTestMethod
import com.intellij.ide.starter.runner.Starter
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.kodein.di.DI
import org.kodein.di.bindSingleton
import software.aws.toolkits.jetbrains.uitests.TestCIServer
import software.aws.toolkits.jetbrains.uitests.clearAwsXmlFile
import software.aws.toolkits.jetbrains.uitests.docTests.prepTestData
import software.aws.toolkits.jetbrains.uitests.docTests.scripts.createReadmeScripts.acceptReadmeTestScript
import software.aws.toolkits.jetbrains.uitests.docTests.scripts.createReadmeScripts.createReadmePromptedToConfirmFolderTestScript
import software.aws.toolkits.jetbrains.uitests.docTests.scripts.createReadmeScripts.makeChangesFlowTestScript
import software.aws.toolkits.jetbrains.uitests.docTests.scripts.createReadmeScripts.rejectReadmeTestScript
import software.aws.toolkits.jetbrains.uitests.docTests.scripts.createReadmeScripts.validateFeatureAvailabilityTestScript
import software.aws.toolkits.jetbrains.uitests.executePuppeteerScript
import software.aws.toolkits.jetbrains.uitests.setupTestEnvironment
import software.aws.toolkits.jetbrains.uitests.useExistingConnectionForTest
import java.io.File
import java.nio.file.Path
import java.nio.file.Paths

class CreateReadmeTest {
    init {
        di = DI {
            extend(di)
            bindSingleton<CIServer>(overrides = true) { TestCIServer }
            val defaults = ConfigurationStorage.instance().defaults.toMutableMap().apply {
                put("LOG_ENVIRONMENT_VARIABLES", (!System.getenv("CI").toBoolean()).toString())
            }

            bindSingleton<ConfigurationStorage>(overrides = true) {
                ConfigurationStorage(this, defaults)
            }
        }
    }

    @BeforeEach
    fun setUpTest() {
        // prep test data - remove readme if it exists
        prepTestData(true)
    }

    @Test
    fun `Validate that the qdoc feature can be invoked`() {
        val testCase = TestCase(
            IdeProductProvider.IC,
            LocalProjectInfo(
                Paths.get("tstData", "qdoc", "createFlow")
            )
        ).withVersion(System.getProperty("org.gradle.project.ideProfileName"))

        // inject connection
        useExistingConnectionForTest()

        Starter.newContext(CurrentTestMethod.hyphenateWithClass(), testCase).apply {
            System.getProperty("ui.test.plugins").split(File.pathSeparator).forEach { path ->
                pluginConfigurator.installPluginFromPath(
                    Path.of(path)
                )
            }

            copyExistingConfig(Paths.get("tstData", "configAmazonQTests"))
            updateGeneralSettings()
        }.runIdeWithDriver()
            .useDriverAndCloseIde {
                waitForProjectOpen()
                // required wait time for the system to be fully ready
                Thread.sleep(30000)

                val result = executePuppeteerScript(validateFeatureAvailabilityTestScript)
                assertThat(result)
                    .contains("Test Successful")
                    .doesNotContain("Error: Test Failed")
            }
    }

    @Test
    fun `You are prompted to confirm selected folder, change folder, or cancel back to choosing CREATE or UPDATE`() {
        val testCase = TestCase(
            IdeProductProvider.IC,
            LocalProjectInfo(
                Paths.get("tstData", "qdoc", "createFlow")
            )
        ).withVersion(System.getProperty("org.gradle.project.ideProfileName"))

        // inject connection
        useExistingConnectionForTest()

        Starter.newContext(CurrentTestMethod.hyphenateWithClass(), testCase).apply {
            System.getProperty("ui.test.plugins").split(File.pathSeparator).forEach { path ->
                pluginConfigurator.installPluginFromPath(
                    Path.of(path)
                )
            }

            copyExistingConfig(Paths.get("tstData", "configAmazonQTests"))
            updateGeneralSettings()
        }.runIdeWithDriver()
            .useDriverAndCloseIde {
                waitForProjectOpen()
                // required wait time for the system to be fully ready
                Thread.sleep(30000)

                val result = executePuppeteerScript(createReadmePromptedToConfirmFolderTestScript)
                assertThat(result)
                    .contains("Test Successful")
                    .doesNotContain("Error: Test Failed")
            }
    }

    @Test
    fun `Make changes button brings you to UPDATE with specific changes flow`() {
        val testCase = TestCase(
            IdeProductProvider.IC,
            LocalProjectInfo(
                Paths.get("tstData", "qdoc", "createFlow")
            )
        ).withVersion(System.getProperty("org.gradle.project.ideProfileName"))

        // inject connection
        useExistingConnectionForTest()

        Starter.newContext(CurrentTestMethod.hyphenateWithClass(), testCase).apply {
            System.getProperty("ui.test.plugins").split(File.pathSeparator).forEach { path ->
                pluginConfigurator.installPluginFromPath(
                    Path.of(path)
                )
            }

            copyExistingConfig(Paths.get("tstData", "configAmazonQTests"))
            updateGeneralSettings()
        }.runIdeWithDriver()
            .useDriverAndCloseIde {
                waitForProjectOpen()
                // required wait time for the system to be fully ready
                Thread.sleep(30000)

                val result = executePuppeteerScript(makeChangesFlowTestScript)
                assertThat(result)
                    .contains("Test Successful")
                    .doesNotContain("Error: Test Failed")
            }
    }

    @Test
    fun `Accept button saves the ReadMe in the appropriate folder`() {
        val testCase = TestCase(
            IdeProductProvider.IC,
            LocalProjectInfo(
                Paths.get("tstData", "qdoc", "createFlow")
            )
        ).withVersion(System.getProperty("org.gradle.project.ideProfileName"))

        // inject connection
        useExistingConnectionForTest()

        Starter.newContext(CurrentTestMethod.hyphenateWithClass(), testCase).apply {
            System.getProperty("ui.test.plugins").split(File.pathSeparator).forEach { path ->
                pluginConfigurator.installPluginFromPath(
                    Path.of(path)
                )
            }

            copyExistingConfig(Paths.get("tstData", "configAmazonQTests"))
            updateGeneralSettings()
        }.runIdeWithDriver()
            .useDriverAndCloseIde {
                waitForProjectOpen()
                // required wait time for the system to be fully ready
                Thread.sleep(30000)

                val readmePath = Paths.get("tstData", "qdoc", "createFlow", "README.md")
                val readme = File(readmePath.toUri())
                assertThat(readme).doesNotExist()

                val result = executePuppeteerScript(acceptReadmeTestScript)
                assertThat(result).doesNotContain("Error: Test Failed")

                val newReadmePath = Paths.get("tstData", "qdoc", "createFlow", "README.md")
                val newReadme = File(newReadmePath.toUri())
                assertThat(newReadme).exists()
                    .content()
                    .contains(
                        "REST",
                        "API"
                    )
            }
    }

    @Test
    fun `Reject button discards the changes`() {
        val testCase = TestCase(
            IdeProductProvider.IC,
            LocalProjectInfo(
                Paths.get("tstData", "qdoc", "createFlow")
            )
        ).withVersion(System.getProperty("org.gradle.project.ideProfileName"))

        // inject connection
        useExistingConnectionForTest()

        Starter.newContext(CurrentTestMethod.hyphenateWithClass(), testCase).apply {
            System.getProperty("ui.test.plugins").split(File.pathSeparator).forEach { path ->
                pluginConfigurator.installPluginFromPath(
                    Path.of(path)
                )
            }

            copyExistingConfig(Paths.get("tstData", "configAmazonQTests"))
            updateGeneralSettings()
        }.runIdeWithDriver()
            .useDriverAndCloseIde {
                waitForProjectOpen()
                // required wait time for the system to be fully ready
                Thread.sleep(30000)

                val readmePath = Paths.get("tstData", "qdoc", "createFlow", "README.md")
                val readme = File(readmePath.toUri())
                assertThat(readme).doesNotExist()

                val result = executePuppeteerScript(rejectReadmeTestScript)
                assertThat(result)
                    .contains("Test Successful")
                    .doesNotContain("Error: Test Failed")

                val newReadmePath = Paths.get("tstData", "qdoc", "createFlow", "README.md")
                val newReadme = File(newReadmePath.toUri())
                assertThat(newReadme).doesNotExist()
            }
    }

    companion object {
        @JvmStatic
        @AfterAll
        fun clearAwsXml() {
            clearAwsXmlFile()
        }

        @JvmStatic
        @BeforeAll
        fun setUp() {
            // Setup test environment
            setupTestEnvironment()
        }
    }
}
