// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.uitests.testTests

import com.intellij.driver.sdk.openFile
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
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.kodein.di.DI
import org.kodein.di.bindSingleton
import software.aws.toolkits.jetbrains.uitests.TestCIServer
import software.aws.toolkits.jetbrains.uitests.clearAwsXmlFile
import software.aws.toolkits.jetbrains.uitests.executePuppeteerScript
import software.aws.toolkits.jetbrains.uitests.setupTestEnvironment
import software.aws.toolkits.jetbrains.uitests.useExistingConnectionForTest
import java.io.File
import java.nio.file.Path
import java.nio.file.Paths

class QTestGenerationChatTest {
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
    fun setUp() {
        setupTestEnvironment()
    }

    @Test
    fun `test method not found error handling`() {
        val testCase = TestCase(
            IdeProductProvider.IC,
            LocalProjectInfo(
                Paths.get("tstData", "qTestGenerationTestProject/")
            )
        ).useRelease(System.getProperty("org.gradle.project.ideProfileName"))

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
                openFile(Paths.get("testModule1", "HappyPath.java").toString())
                Thread.sleep(30000)
                val result = executePuppeteerScript(testMethodNotFoundErrorScript)
                assertTrue(result.contains("new tab opened"))
                assertTrue(result.contains("Error message displayed correctly"))
                assertTrue(result.contains("Input field re-enabled after error"))
                assertTrue(result.contains("Feedback button found with correct text after error"))
            }
    }

    @Test
    fun `test cancel button during test generation`() {
        val testCase = TestCase(
            IdeProductProvider.IC,
            LocalProjectInfo(
                Paths.get("tstData", "qTestGenerationTestProject/")
            )
        ).useRelease(System.getProperty("org.gradle.project.ideProfileName"))

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
                openFile(Paths.get("testModule1", "HappyPath.java").toString())
                Thread.sleep(30000)
                val result = executePuppeteerScript(testCancelButtonScript)
                assertTrue(result.contains("new tab opened"))
                assertTrue(result.contains("Progress bar text displayed"))
                assertTrue(result.contains("Cancel button found"))
                assertTrue(result.contains("Cancel button clicked"))
                assertTrue(result.contains("Test generation cancelled successfully"))
                assertTrue(result.contains("Input field re-enabled after cancellation"))
                assertTrue(result.contains("Feedback button found with correct text"))
            }
    }

    @Test
    fun `test documentation generation error handling`() {
        val testCase = TestCase(
            IdeProductProvider.IC,
            LocalProjectInfo(
                Paths.get("tstData", "qTestGenerationTestProject/")
            )
        ).useRelease(System.getProperty("org.gradle.project.ideProfileName"))

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
                openFile(Paths.get("testModule1", "HappyPath.java").toString())
                Thread.sleep(30000)
                val result = executePuppeteerScript(testDocumentationErrorScript)
                assertTrue(result.contains("new tab opened"))
                assertTrue(result.contains("Error message displayed correctly"))
                assertTrue(result.contains("Input field re-enabled after error"))
                assertTrue(result.contains("Feedback button found with correct text after error"))
            }
    }

    @Test
    fun `test remove function error handling`() {
        val testCase = TestCase(
            IdeProductProvider.IC,
            LocalProjectInfo(
                Paths.get("tstData", "qTestGenerationTestProject/")
            )
        ).useRelease(System.getProperty("org.gradle.project.ideProfileName"))

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
                openFile(Paths.get("testModule1", "HappyPath.java").toString())
                Thread.sleep(30000)
                val result = executePuppeteerScript(testRemoveFunctionErrorScript)
                assertTrue(result.contains("new tab opened"))
                assertTrue(result.contains("Error message displayed correctly"))
                assertTrue(result.contains("Input field re-enabled after error"))
                assertTrue(result.contains("Feedback button found with correct text after error"))
            }
    }

    @Test
    fun `can run a test from the chat`() {
        val testCase = TestCase(
            IdeProductProvider.IC,
            LocalProjectInfo(
                Paths.get("tstData", "qTestGenerationTestProject")
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
                val result = executePuppeteerScript(testNoFilePathScript)
                assertTrue(result.contains("new tab opened"))
                assertTrue(result.contains("a source file open right now that I can generate a test for"))
            }
    }

    @Test
    fun `test happy path from the chat`() {
        val testCase = TestCase(
            IdeProductProvider.IC,
            LocalProjectInfo(
                Paths.get("tstData", "qTestGenerationTestProject/")
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
                openFile(Paths.get("testModule1", "HappyPath.java").toString())
                Thread.sleep(30000)
                val result = executePuppeteerScript(testHappyPathScript)
                assertTrue(result.contains("new tab opened"))
                assertTrue(result.contains("View Diff opened"))
                assertTrue(result.contains("Result Accepted"))
                assertTrue(result.contains("Unit test generation completed."))
                assertTrue(result.contains("Input field re-enabled after acceptance"))
                assertTrue(result.contains("Feedback button found with correct text"))
            }
    }

    @Test
    fun `test expected error path from the chat`() {
        val testCase = TestCase(
            IdeProductProvider.IC,
            LocalProjectInfo(
                Paths.get("tstData", "qTestGenerationTestProject/")
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
                openFile(Paths.get("testModule1", "ErrorPath.java").toString())
                Thread.sleep(30000)
                val result = executePuppeteerScript(expectedErrorPath)
                assertTrue(result.contains("new tab opened"))
                assertTrue(result.contains("Test generation complete with expected error"))
            }
    }

    @Test
    fun `test unsupported language error path from the chat`() {
        val testCase = TestCase(
            IdeProductProvider.IC,
            LocalProjectInfo(
                Paths.get("tstData", "qTestGenerationTestProject/")
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
                openFile(Paths.get("testModule2", "UnSupportedLanguage.kt").toString())
                Thread.sleep(30000)
                val result = executePuppeteerScript(unsupportedLanguagePath)
                assertTrue(result.contains("new tab opened"))
                assertTrue(result.contains("Test generation complete with expected error"))
            }
    }

    @Test
    fun `test reject path from the chat`() {
        val testCase = TestCase(
            IdeProductProvider.IC,
            LocalProjectInfo(
                Paths.get("tstData", "qTestGenerationTestProject/")
            )
        ).useRelease(System.getProperty("org.gradle.project.ideProfileName"))

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
                openFile(Paths.get("testModule1", "HappyPath.java").toString())
                Thread.sleep(30000)
                val result = executePuppeteerScript(testRejectPathScript)
                assertTrue(result.contains("new tab opened"))
                assertTrue(result.contains("View Diff opened"))
                assertTrue(result.contains("Result Reject"))
                assertTrue(result.contains("Unit test generation completed."))
                assertTrue(result.contains("Input field re-enabled after rejection"))
                assertTrue(result.contains("Feedback button found with correct text"))
            }
    }

    @Test
    fun `test NL error from the chat`() {
        val testCase = TestCase(
            IdeProductProvider.IC,
            LocalProjectInfo(
                Paths.get("tstData", "qTestGenerationTestProject/")
            )
        ).useRelease(System.getProperty("org.gradle.project.ideProfileName"))

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
                openFile(Paths.get("testModule1", "HappyPath.java").toString())
                Thread.sleep(30000)
                val result = executePuppeteerScript(testNLErrorPathScript)
                assertTrue(result.contains("new tab opened"))
                assertTrue(result.contains("Command entered: /test /something/"))
                assertTrue(result.contains("Error message displayed correctly"))
            }
    }

    @Test
    fun `test progress bar during test generation`() {
        val testCase = TestCase(
            IdeProductProvider.IC,
            LocalProjectInfo(
                Paths.get("tstData", "qTestGenerationTestProject/")
            )
        ).useRelease(System.getProperty("org.gradle.project.ideProfileName"))

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
                openFile(Paths.get("testModule1", "HappyPath.java").toString())
                Thread.sleep(30000)
                val result = executePuppeteerScript(testProgressBarScript)
                assertTrue(result.contains("new tab opened"))
                assertTrue(result.contains("Progress bar text displayed"))
                assertTrue(result.contains("Test generation completed successfully"))
            }
    }

    companion object {
        @JvmStatic
        @AfterAll
        fun clearAwsXml() {
            clearAwsXmlFile()
        }
    }
}
