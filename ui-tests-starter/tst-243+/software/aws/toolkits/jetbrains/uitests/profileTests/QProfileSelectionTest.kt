// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.uitests.profileTests

import com.intellij.driver.client.Driver
import com.intellij.driver.sdk.ui.components.common.editor
import com.intellij.driver.sdk.waitForProjectOpen
import com.intellij.driver.sdk.ui.components.common.ideFrame
import com.intellij.driver.sdk.openFile
import com.intellij.driver.sdk.ui.ui
import com.intellij.driver.sdk.ui.xQuery
import java.awt.event.KeyEvent
import com.intellij.ide.starter.ci.CIServer
import com.intellij.ide.starter.config.ConfigurationStorage
import com.intellij.ide.starter.di.di
import com.intellij.ide.starter.driver.engine.runIdeWithDriver
import com.intellij.ide.starter.ide.IdeProductProvider
import com.intellij.ide.starter.junit5.hyphenateWithClass
import com.intellij.ide.starter.models.TestCase
import com.intellij.ide.starter.project.LocalProjectInfo
import com.intellij.ide.starter.report.AllureHelper.step
import com.intellij.ide.starter.runner.CurrentTestMethod
import com.intellij.ide.starter.runner.Starter
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.kodein.di.DI
import org.kodein.di.bindSingleton
import software.aws.toolkits.jetbrains.uitests.TestCIServer
import software.aws.toolkits.jetbrains.uitests.clearAwsXmlFile
import software.aws.toolkits.jetbrains.uitests.copyExistingConfig
import software.aws.toolkits.jetbrains.uitests.executePuppeteerScript
import software.aws.toolkits.jetbrains.uitests.setupMultipleProfilesForTest
import software.aws.toolkits.jetbrains.uitests.setupMultipleProfilesWithSelectionForTest
import software.aws.toolkits.jetbrains.uitests.setupTestEnvironment
import software.aws.toolkits.jetbrains.uitests.useExistingConnectionForTest
import software.aws.toolkits.jetbrains.uitests.writeToAwsXml
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardOpenOption

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class QProfileSelectionTest {

    private val setupContent = """public class Example {
    public static void main(String[] args) {
        int a = 10;
        int b = 20;
        int c = add(a, b);
        System.out.println("The sum of a and b is: " + c);
    }
    
}"""



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

    @BeforeEach
    fun resetTestFile() {
        val path = Paths.get("tstData", "Hello", "Example.java")

        Files.createDirectories(path.parent)
        Files.write(
            path,
            setupContent.toByteArray(),
            StandardOpenOption.CREATE,
            StandardOpenOption.TRUNCATE_EXISTING
        )
    }

    @Test
    fun `Test single dev profile user accounts`() {
        val testCase = TestCase(
            IdeProductProvider.IC,
            LocalProjectInfo(
                Paths.get("tstData", "Hello")
            )
        ).withVersion(System.getProperty("org.gradle.project.ideProfileName"))

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
                // Wait for the system to be fully ready
                Thread.sleep(10000)

                step("Test chat shown directly for users with single profile") {
                    val result = executePuppeteerScript(testActiveToolWindowPage)
                    assertThat(result).contains("Chat is shown")
                }

                step("Test changing same profile A -> A does nothing") {
                    changeProfileAndVerify(this, false)
                }

                step("Dev features work with single profile user") {
                    triggerDevFeatures(this, true)
                }
            }
    }

    @Test
    fun `Test 2+ dev profile user account fresh login`() {
        val testCase = TestCase(
            IdeProductProvider.IC,
            LocalProjectInfo(
                Paths.get("tstData", "Hello")
            )
        ).withVersion(System.getProperty("org.gradle.project.ideProfileName"))

        // Configure test with multiple profiles
        setupMultipleProfilesForTest()

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
                // Wait for the system to be fully ready
                Thread.sleep(10000)

                step("profile selector shown when signed in with no selection") {
                    val result = executePuppeteerScript(testActiveToolWindowPage)
                    assertThat(result).contains("Profile selector is shown")
                }

                step("Q services not triggered when no profile selected"){
                    triggerDevFeatures(this, false)
                }

                step("Switching profile notifies and shows chat"){
                    changeProfileAndVerify(this, true)
                    val result = executePuppeteerScript(testActiveToolWindowPage)
                    assertThat(result).contains("Chat is shown")
                }

                step("Q features work with selected profile"){
                    triggerDevFeatures(this, true)
                }

                step("Changing profile A -> A does nothing") {
                    changeProfileAndVerify(this, false)
                }

            }
    }

    @Test
    fun `Test 2+ profile user account with selected profile startup`() {
        val testCase = TestCase(
            IdeProductProvider.IC,
            LocalProjectInfo(
                Paths.get("tstData", "Hello")
            )
        ).withVersion(System.getProperty("org.gradle.project.ideProfileName"))

        setupMultipleProfilesWithSelectionForTest()

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
                // Wait for the system to be fully read
                Thread.sleep(10000)

                // tab starts closed and needs to be clicked to open
                ideFrame {
                    x(xQuery { byAccessibleName("Amazon Q Chat") }).click()
                }

                step("Chat is shown directly on startup") {
                    val result = executePuppeteerScript(testActiveToolWindowPage)
                    assertThat(result).contains("Chat is shown")
                }

                step("Changing profile A -> A does nothing") {
                    changeProfileAndVerify(this, false)
                }

                step("Dev features work with selected profile") {
                    triggerDevFeatures(this, true)
                }
            }
    }

    @AfterAll
    fun clearAwsXml() {
        clearAwsXmlFile()

        val path = Paths.get("tstData", "Hello", "Example.java")
        Files.write(path, ByteArray(0), StandardOpenOption.TRUNCATE_EXISTING)
    }

    private fun changeProfileAndVerify(driver: Driver, switchingProfiles: Boolean) =
        driver.ideFrame {
            // Click Amazon Q button in status bar
            x(xQuery {
                byAccessibleName("Amazon Q") and byJavaClass("com.intellij.openapi.wm.impl.status.MultipleTextValues")
            }).click()
            Thread.sleep(100)

            driver.ui.keyboard {
                // navigate and select "Change Profile" in the popup menu
                key(KeyEvent.VK_UP)
                key(KeyEvent.VK_UP)
                key(KeyEvent.VK_ENTER)

                //wait for list to load
                Thread.sleep(3000)

                // profile combobox selection and select connection
                key(KeyEvent.VK_TAB)
                if(switchingProfiles){
                    // navigate past (current)
                    key(KeyEvent.VK_DOWN)
                }
                key(KeyEvent.VK_DOWN)
                key(KeyEvent.VK_ENTER)

                // confirm selection
                key(KeyEvent.VK_ENTER)
            }

            // Verify notification behavior
            Thread.sleep(100)
            val notification = x(xQuery { byVisibleText("You changed your profile") })
            if(switchingProfiles){
                assertTrue { notification.present() }
            } else {
                assertTrue { notification.notPresent() }
            }
        }

    private fun triggerDevFeatures(driver: Driver, isProfileSelected: Boolean) {

        //trigger inline completion
        var originalText: String? = null
        var afterSuggestion: String? = null
        driver.ideFrame {
            driver.openFile("Example.java")
            editor {
                originalText = text
                moveCaretToOffset(text.length - 2)

                driver.ui.keyboard {
                    pressing(KeyEvent.VK_ALT) {
                        key(KeyEvent.VK_C)
                    }
                }
                Thread.sleep(2000)

                val hintExists = editor.getInlayModel().getInlineElementsInRange(0, text.length).isNotEmpty()
                if(isProfileSelected) {
                    assertThat(hintExists).isTrue()
                }
                else {
                    assertThat(hintExists).isFalse()
                }
                driver.ui.keyboard {
                    key(KeyEvent.VK_TAB)
                }
                afterSuggestion = text
                text = setupContent
            }
        }
        if(isProfileSelected) {
            assertThat(afterSuggestion?.replace(Regex("\\s+"), " ")?.trim())
                .isNotEqualTo(originalText?.replace(Regex("\\s+"), " ")?.trim())
        }
        else {
            assertThat(afterSuggestion?.replace(Regex("\\s+"), " ")?.trim())
                .isEqualTo(originalText?.replace(Regex("\\s+"), " ")?.trim())
        }

        //check q features
        driver.ideFrame {
            val editor = x(xQuery { byClass("EditorComponentImpl") })
            editor.click()
            editor.rightClick()

            // Wait for context menu to appear
            Thread.sleep(100)

            // Check if "Amazon Q" option is present
            val amazonQInMenu = x(xQuery {
                byVisibleText("Amazon Q") and byJavaClass("com.intellij.openapi.actionSystem.impl.ActionMenu")
            })
            if(isProfileSelected) {
                assertTrue { amazonQInMenu.present() }
            }
            else {
                assertTrue { amazonQInMenu.notPresent() }
            }
        }
    }
}
