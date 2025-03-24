// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.uitests.docTests.createReadmeTests

import com.intellij.driver.sdk.ui.ui
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
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.kodein.di.DI
import org.kodein.di.bindSingleton
import software.aws.toolkits.jetbrains.uitests.TestCIServer
import software.aws.toolkits.jetbrains.uitests.clearAwsXmlFile
import software.aws.toolkits.jetbrains.uitests.docTests.prepTestData
import software.aws.toolkits.jetbrains.uitests.docTests.scripts.createReadmeScripts.acceptReadmeTestScript
import software.aws.toolkits.jetbrains.uitests.docTests.scripts.createReadmeScripts.createReadmeSubFolderPostFolderChangeTestScript
import software.aws.toolkits.jetbrains.uitests.docTests.scripts.createReadmeScripts.createReadmeSubFolderPreFolderChangeTestScript
import software.aws.toolkits.jetbrains.uitests.executePuppeteerScript
import software.aws.toolkits.jetbrains.uitests.setupTestEnvironment
import software.aws.toolkits.jetbrains.uitests.useExistingConnectionForTest
import java.awt.event.KeyEvent
import java.io.File
import java.nio.file.Path
import java.nio.file.Paths

class CreateReadmeWorkspacesTest {
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
    fun `Create readme with single-root workspace, root folder returns a readme` () {
        val testCase = TestCase(
            IdeProductProvider.IC,
            LocalProjectInfo(
                Paths.get("tstData", "qdoc", "createFlow")
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
    fun `Create readme with single-root workspace, in a subfolder returns a readme`() {
        val testCase = TestCase(
            IdeProductProvider.IC,
            LocalProjectInfo(
                Paths.get("tstData", "qdoc", "createFlow")
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

                val readmePath = Paths.get("tstData", "qdoc", "createFlow", "src", "README.md")
                val readme = File(readmePath.toUri())
                assertFalse(readme.exists())

                val result = executePuppeteerScript(createReadmeSubFolderPreFolderChangeTestScript)
                this.ui.robot.pressAndReleaseKey(KeyEvent.VK_RIGHT)
                this.ui.robot.pressAndReleaseKey(KeyEvent.VK_DOWN)
                this.ui.robot.pressAndReleaseKey(KeyEvent.VK_ENTER)
                val result2 = executePuppeteerScript(createReadmeSubFolderPostFolderChangeTestScript)

                assertFalse(result.contains("Error: Test Failed"))
                assertFalse(result2.contains("Error: Test Failed"))

                if (result.contains("Error: Test Failed") || result2.contains("Error: Test Failed")) {
                    println("result: $result")
                    println("result2: $result2")
                }

                val newReadmePath = Paths.get("tstData", "qdoc", "createFlow", "src", "README.md")
                val newReadme = File(newReadmePath.toUri())
                assertTrue(newReadme.exists())
            }
    }

    @Test
    fun `Create readme with multi-root workspace returns a readme`() {
        val testCase = TestCase(
            IdeProductProvider.IC,
            LocalProjectInfo(
                Paths.get("tstData", "qdoc")
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

                val readmePath = Paths.get("tstData", "qdoc", "README.md")
                val readme = File(readmePath.toUri())
                assertFalse(readme.exists())

                val result = executePuppeteerScript(acceptReadmeTestScript)
                assertFalse(result.contains("Error: Test Failed"))
                if (result.contains("Error: Test Failed")) {
                    println("result: $result")
                }

                val newReadmePath = Paths.get("tstData", "qdoc", "README.md")
                val newReadme = File(newReadmePath.toUri())
                assertTrue(newReadme.exists())
                val readmeContents = newReadme.readText()
                assertTrue(readmeContents.contains("REST"))
                assertTrue(readmeContents.contains("API"))
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
