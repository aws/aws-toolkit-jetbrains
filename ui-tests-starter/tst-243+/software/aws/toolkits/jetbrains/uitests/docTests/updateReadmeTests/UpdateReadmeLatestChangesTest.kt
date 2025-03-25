// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.uitests.docTests.updateReadmeTests

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
import software.aws.toolkits.jetbrains.uitests.docTests.scripts.updateReadmeLatestChangesConfirmOptionsTestScript
import software.aws.toolkits.jetbrains.uitests.docTests.scripts.updateReadmeLatestChangesMakeChangesFlowTestScript
import software.aws.toolkits.jetbrains.uitests.docTests.scripts.updateReadmeLatestChangesTestScript
import software.aws.toolkits.jetbrains.uitests.executePuppeteerScript
import software.aws.toolkits.jetbrains.uitests.setupTestEnvironment
import software.aws.toolkits.jetbrains.uitests.useExistingConnectionForTest
import java.io.File
import java.nio.file.Path
import java.nio.file.Paths

class UpdateReadmeLatestChangesTest {
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
        // prep test data - restore readme contents
        prepTestData(false)
    }

    @Test
    fun `Make Changes button leads to UPDATE with specific changes flow`() {
        val testCase = TestCase(
            IdeProductProvider.IC,
            LocalProjectInfo(
                Paths.get("tstData", "qdoc", "updateFlow")
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
                // required wait time for the system to be fully ready
                Thread.sleep(30000)

                val result = executePuppeteerScript(updateReadmeLatestChangesMakeChangesFlowTestScript)

                if (result.contains("Error: Test Failed")) {
                    println("result: $result")
                }

                assertThat(result)
                    .contains("Test Successful")
                    .doesNotContain("Error: Test Failed")
            }
    }

    @Test
    fun `Prompted to confirm selected folder`() {
        val testCase = TestCase(
            IdeProductProvider.IC,
            LocalProjectInfo(
                Paths.get("tstData", "qdoc", "updateFlow")
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
                // required wait time for the system to be fully ready
                Thread.sleep(30000)

                val result = executePuppeteerScript(updateReadmeLatestChangesConfirmOptionsTestScript)

                if (result.contains("Error: Test Failed")) {
                    println("result: $result")
                }

                assertThat(result)
                    .contains("Test Successful")
                    .doesNotContain("Error: Test Failed")
            }
    }

    @Test
    fun `UpdateReadme with latest changes returns an updated Readme`() {
        val testCase = TestCase(
            IdeProductProvider.IC,
            LocalProjectInfo(
                Paths.get("tstData", "qdoc", "updateFlow")
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
                // required wait time for the system to be fully ready
                Thread.sleep(30000)

                val result = executePuppeteerScript(updateReadmeLatestChangesTestScript)

                if (result.contains("Error: Test Failed")) {
                    println("result: $result")
                }

                val readmePath = Paths.get("tstData", "qdoc", "updateFlow", "README.md")
                val readme = File(readmePath.toUri())
                assertThat(readme).exists()
                assertThat(readme).content()
                    .containsIgnoringCase("sample/qdoc")
                    .containsIgnoringCase("healthcontroller.java")
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
        fun setUpTestClass() {
            // Setup test environment
            setupTestEnvironment()
        }
    }
}
