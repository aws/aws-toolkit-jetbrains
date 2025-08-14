// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.uitests.profileTests

import com.intellij.driver.sdk.waitForProjectOpen
import com.intellij.driver.sdk.ui.components.common.ideFrame
import com.intellij.driver.sdk.ui.notPresent
import com.intellij.driver.sdk.ui.shouldBe
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
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.kodein.di.DI
import org.kodein.di.bindSingleton
import software.aws.toolkits.jetbrains.uitests.TestCIServer
import software.aws.toolkits.jetbrains.uitests.clearAwsXmlFile
import software.aws.toolkits.jetbrains.uitests.copyExistingConfig
import software.aws.toolkits.jetbrains.uitests.executePuppeteerScript
import software.aws.toolkits.jetbrains.uitests.setupTestEnvironment
import software.aws.toolkits.jetbrains.uitests.useExistingConnectionForTest
import software.aws.toolkits.jetbrains.uitests.writeToAwsXml
import java.io.File
import java.nio.file.Path
import java.nio.file.Paths

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class QProfileSelectionTest {

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
    fun `Test profile selector shown for users with multiple profiles`() {
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
                Thread.sleep(30000)

                val result = executePuppeteerScript(testProfileSelectorShown)
                assertThat(result).contains("Profile selector is shown")
            }
    }

    @Test
//    @SsoLogin("single_profile_user")
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
                    val result = executePuppeteerScript(testChatShownDirectly)
                    assertThat(result).contains("Chat is shown directly")
                }

                step("Test changing same profile A -> A does nothing") {
                    ideFrame {
                        // Click Amazon Q button in status bar
                        x(xQuery { byAccessibleName("Amazon Q") }).click()
                        Thread.sleep(100)

                        ui.keyboard {
                            // navigate and select "Change Profile" in the popup menu
                            key(KeyEvent.VK_UP)
                            key(KeyEvent.VK_UP)
                            key(KeyEvent.VK_ENTER)

                            //wait for list to load
                            Thread.sleep(3000)

                            // profile combobox selection and select (current) connection
                            key(KeyEvent.VK_TAB)
                            key(KeyEvent.VK_DOWN)
                            key(KeyEvent.VK_ENTER)

                            // confirm selection
                            key(KeyEvent.VK_ENTER)
                        }

                        // Verify no notification appears
                        assertThat {
                            x(xQuery { byClass("NotificationCenterPanel").and(byText("You changed your profile")) })
                            .shouldBe(notPresent)
                        }
                    }
                }
            }
    }

    @Test
    fun `Test profile switching clears chat history`() {
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
                Thread.sleep(30000)

                val result = executePuppeteerScript(testProfileSwitching)
                assertThat(result).contains("Profile switch confirmation shown")
                assertThat(result).contains("Chat history cleared")
            }
    }

    @Test
    fun `Test IDE restart remembers profile selection`() {
        val testCase = TestCase(
            IdeProductProvider.IC,
            LocalProjectInfo(
                Paths.get("tstData", "Hello")
            )
        ).withVersion(System.getProperty("org.gradle.project.ideProfileName"))

        // Configure test with multiple profiles and a selected profile
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
                // Wait for the system to be fully ready
                Thread.sleep(30000)

                val result = executePuppeteerScript(testProfileRemembered)
                assertThat(result).contains("Chat is shown directly")
                assertThat(result).contains("Profile selection remembered")
            }
    }

    @AfterAll
    fun clearAwsXml() {
        clearAwsXmlFile()
    }

    companion object {

        fun setupMultipleProfilesForTest() {
            val testStartUrl = System.getenv("TEST_START_URL")
            val testRegion = System.getenv("TEST_REGION")
            val configContent = """
            <application>
                <component name="authManager">
                    <option name="ssoProfiles">
                        <list>
                            <ManagedSsoProfile>
                                <option name="scopes">
                                    <list>
                                        <option value="codewhisperer:conversations" />
                                        <option value="codewhisperer:transformations" />
                                        <option value="codewhisperer:taskassist" />
                                        <option value="codewhisperer:completions" />
                                        <option value="codewhisperer:analysis" />
                                    </list>
                                </option>
                                <option name="ssoRegion" value="$testRegion" />
                                <option name="startUrl" value="$testStartUrl" />
                            </ManagedSsoProfile>
                        </list>
                    </option>
                </component>
                <component name="connectionPinningManager">
                    <option name="pinnedConnections">
                        <map>
                            <entry key="aws.codewhisperer" value="sso;$testRegion;$testStartUrl" />
                            <entry key="aws.q" value="sso;$testRegion;$testStartUrl" />
                        </map>
                    </option>
                </component>
                <component name="qProfileStates">
                    <option name="connectionIdToProfileList">
                        <map>
                            <entry key="sso;$testRegion;$testStartUrl" value="2" />
                        </map>
                    </option>
                </component>
                <component name="meetQPage">
                    <option name="shouldDisplayPage" value="false" />
                </component>
            </application>
            """.trimIndent()
            writeToAwsXml(configContent)
        }

        fun setupMultipleProfilesWithSelectionForTest() {
            val testStartUrl = System.getenv("TEST_START_URL")
            val testRegion = System.getenv("TEST_REGION")
            val configContent = """
            <application>
                <component name="authManager">
                    <option name="ssoProfiles">
                        <list>
                            <ManagedSsoProfile>
                                <option name="scopes">
                                    <list>
                                        <option value="codewhisperer:conversations" />
                                        <option value="codewhisperer:transformations" />
                                        <option value="codewhisperer:taskassist" />
                                        <option value="codewhisperer:completions" />
                                        <option value="codewhisperer:analysis" />
                                    </list>
                                </option>
                                <option name="ssoRegion" value="$testRegion" />
                                <option name="startUrl" value="$testStartUrl" />
                            </ManagedSsoProfile>
                        </list>
                    </option>
                </component>
                <component name="connectionPinningManager">
                    <option name="pinnedConnections">
                        <map>
                            <entry key="aws.codewhisperer" value="sso;$testRegion;$testStartUrl" />
                            <entry key="aws.q" value="sso;$testRegion;$testStartUrl" />
                        </map>
                    </option>
                </component>
                <component name="qProfileStates">
                    <option name="connectionIdToProfileList">
                        <map>
                            <entry key="sso;$testRegion;$testStartUrl" value="2" />
                        </map>
                    </option>
                    <option name="connectionIdToActiveProfile">
                        <map>
                            <entry key="sso;$testRegion;$testStartUrl">
                                <QRegionProfile>
                                    <option name="profileName" value="TestProfile" />
                                    <option name="arn" value="" />
                                </QRegionProfile>
                            </entry>
                        </map>
                    </option>
                </component>
                <component name="meetQPage">
                    <option name="shouldDisplayPage" value="false" />
                </component>
            </application>
            """.trimIndent()
            writeToAwsXml(configContent)
        }
    }
}
