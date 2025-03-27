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
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
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
                assertTrue(result.contains("Test Successful"))
                assertFalse(result.contains("Error: Test Failed"))
                if (result.contains("Error: Test Failed")) {
                    println("result: $result")
                }
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
                assertTrue(result.contains("Test Successful"))
                assertFalse(result.contains("Error: Test Failed"))
                if (result.contains("Error: Test Failed")) {
                    println("result: $result")
                }
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
                assertTrue(result.contains("Test Successful"))
                assertFalse(result.contains("Error: Test Failed"))
                if (result.contains("Error: Test Failed")) {
                    println("result: $result")
                }
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
                assertFalse(readme.exists())

                val result = executePuppeteerScript(acceptReadmeTestScript)
                assertFalse(result.contains("Error: Test Failed"))
                if (result.contains("Error: Test Failed")) {
                    println("result: $result")
                }

                val newReadmePath = Paths.get("tstData", "qdoc", "createFlow", "README.md")
                val newReadme = File(newReadmePath.toUri())
                assertTrue(newReadme.exists())
                assertTrue(newReadme.readText().contains("REST"))
                assertTrue(newReadme.readText().contains("API"))
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
                assertFalse(readme.exists())

                val result = executePuppeteerScript(rejectReadmeTestScript)
                assertTrue(result.contains("Test Successful"))
                assertFalse(result.contains("Error: Test Failed"))

                if (result.contains("Error: Test Failed")) {
                    println("result: $result")
                }

                val newReadmePath = Paths.get("tstData", "qdoc", "createFlow", "README.md")
                val newReadme = File(newReadmePath.toUri())
                assertFalse(newReadme.exists())
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
