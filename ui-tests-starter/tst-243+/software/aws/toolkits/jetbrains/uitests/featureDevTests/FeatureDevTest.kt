// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.uitests.featureDevTests

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
import org.junit.jupiter.api.AfterEach
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
import java.io.FileOutputStream
import java.nio.file.Path
import java.nio.file.Paths

class FeatureDevTest {
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
        // Setup test environment
        setupTestEnvironment()
    }

    @AfterEach
    fun resetTestProject() {
        val operationFile = Paths.get("tstData", "FeatureDevE2ETestFolder", "operation.js").toFile()
        FileOutputStream(operationFile).channel.truncate(0)

        val changelogFile = Paths.get("tstData", "FeatureDevE2ETestFolder", "CHANGELOG.md").toFile()
        FileOutputStream(changelogFile).channel.truncate(0)
    }

    @Test
    fun `Accept initial code generation`() {
        val testCase = TestCase(
            IdeProductProvider.IC,
            LocalProjectInfo(
                Paths.get("tstData", "FeatureDevE2ETestFolder")
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

                val result = executePuppeteerScript(testAcceptInitalCode)
                assertThat(result).contains("Success: /dev ends the conversation successfully.")
            }
    }

    @Test
    fun `Iterate code generation`() {
        val testCase = TestCase(
            IdeProductProvider.IC,
            LocalProjectInfo(
                Paths.get("tstData", "FeatureDevE2ETestFolder")
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

                val result = executePuppeteerScript(testIterateCodeGen)
                assertThat(result).contains("Success: /dev ends the conversation successfully.")
            }
    }

    @Test
    fun `Start new code generation`() {
        val testCase = TestCase(
            IdeProductProvider.IC,
            LocalProjectInfo(
                Paths.get("tstData", "FeatureDevE2ETestFolder")
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

                val result = executePuppeteerScript(testNewCodeGen)
                assertThat(result).contains("Success: /dev ends the conversation successfully.")
            }
    }

    @Test
    fun `Accept partial code generation`() {
        val testCase = TestCase(
            IdeProductProvider.IC,
            LocalProjectInfo(
                Paths.get("tstData", "FeatureDevE2ETestFolder")
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

                val result = executePuppeteerScript(testPartialCodeGen)
                assertThat(result).contains("Success: /dev ends the conversation successfully.")
            }
    }

    @Test
    fun `Stop and restart code generation`() {
        val testCase = TestCase(
            IdeProductProvider.IC,
            LocalProjectInfo(
                Paths.get("tstData", "FeatureDevE2ETestFolder")
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

                val result = executePuppeteerScript(testStopAndRestartCodeGen)
                assertThat(result).contains("Success: /dev ends the conversation successfully.")
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
