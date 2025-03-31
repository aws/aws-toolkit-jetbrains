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
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
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
                assertThat(result)
                    .contains(
                        "new tab opened",
                        "Error message displayed correctly",
                        "Input field re-enabled after error",
                        "Feedback button found with correct text after error"
                    )
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
                assertThat(result)
                    .contains(
                        "new tab opened",
                        "Progress bar text displayed",
                        "Cancel button found",
                        "Cancel button clicked",
                        "Test generation cancelled successfully",
                        "Input field re-enabled after cancellation",
                        "Feedback button found with correct text"
                    )
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
                assertThat(result)
                    .contains(
                        "new tab opened",
                        "Error message displayed correctly",
                        "Input field re-enabled after error",
                        "Feedback button found with correct text after error"
                    )
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
                assertThat(result)
                    .contains(
                        "new tab opened",
                        "Error message displayed correctly",
                        "Input field re-enabled after error",
                        "Feedback button found with correct text after error"
                    )
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
                assertThat(result)
                    .contains("new tab opened")
                    .contains("a source file open right now that I can generate a test for")
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

                assertThat(result)
                    .contains(
                        "new tab opened",
                        "View Diff opened",
                        "Result Accepted",
                        "Unit test generation completed."
                    )
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

                assertThat(result)
                    .contains(
                        "new tab opened",
                        "Test generation complete with expected error"
                    )
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

                assertThat(result)
                    .contains(
                        "new tab opened",
                        "Test generation complete with expected error"
                    )
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
                assertThat(result)
                    .contains(
                        "new tab opened",
                        "View Diff opened",
                        "Result Reject",
                        "Unit test generation completed.",
                        "Input field re-enabled after rejection",
                        "Feedback button found with correct text"
                    )
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
                assertThat(result)
                    .contains(
                        "new tab opened",
                        "Command entered: /test /something/",
                        "Error message displayed correctly"
                    )
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
                assertThat(result)
                    .contains(
                        "new tab opened",
                        "Progress bar text displayed",
                        "Test generation completed successfully"
                    )
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
