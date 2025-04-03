// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.uitests.inlineTests

import com.intellij.driver.sdk.openFile
import com.intellij.driver.sdk.ui.ui
import org.assertj.core.api.Assertions.assertThat
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
import org.junit.jupiter.api.Test
import org.kodein.di.DI
import org.kodein.di.bindSingleton
import software.aws.toolkits.jetbrains.uitests.TestCIServer
import software.aws.toolkits.jetbrains.uitests.useExistingConnectionForTest
import java.io.File
import java.nio.file.Path
import java.nio.file.Paths
import org.junit.jupiter.api.BeforeEach
import java.nio.file.Files
import java.nio.file.StandardOpenOption
import com.intellij.driver.sdk.ui.components.common.editor
import com.intellij.driver.sdk.ui.components.common.ideFrame
import java.awt.event.KeyEvent

class AmazonQInlineCompletionE2ETest {
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
    fun resetTestFile() {
        val originalContent = """public class MathClass {
    public static void main(String[] args) {
        int a = 10;
        int b = 20;
        int c = add(a, b);
        System.out.println("The sum of a and b is: " + c);
    }
    
}"""

        val path = Paths.get("tstData", "inlineCompletionProject", "MathClass.java")

        Files.createDirectories(path.parent)
        Files.write(
            path,
            originalContent.toByteArray(),
            StandardOpenOption.CREATE,
            StandardOpenOption.TRUNCATE_EXISTING
        )
    }

    // Test Case 1: Manual invoke inline completion
    @Test
    fun `test manual invoke inline completion`() {
        val testCase = TestCase(
            IdeProductProvider.IC,
            LocalProjectInfo(
                Paths.get("tstData", "InlineCompletionProject")
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
                Thread.sleep(2000)
                var originalText: String? = null
                var afterSuggestion: String? = null

                ideFrame {
                    // Open file
                    openFile("MathClass.java")
                    // Editor operations
                    editor {
                        originalText = text
                        // Move to position for recommendation
                        moveCaretToOffset(text.length - 2)

                        // Trigger completion and wait for result
                        ui.keyboard {
                            pressing(KeyEvent.VK_ALT) {
                                key(KeyEvent.VK_C)
                            }
                        }
                        Thread.sleep(1000)

                        // Ensure inlineElement exists
                        val hintExists = editor.getInlayModel().getInlineElementsInRange(0, text.length).isNotEmpty()
                        assertThat(hintExists).isTrue()
                        // Accept completion
                        ui.keyboard {
                            key(KeyEvent.VK_TAB)
                        }
                        afterSuggestion = text
                    }
                }
                assertThat(afterSuggestion).isNotEqualTo(originalText)
            }
    }

    // Test Case 2: Manual trigger + reject
    @Test
    fun `test manual trigger with rejection`() {
        val testCase = TestCase(
            IdeProductProvider.IC,
            LocalProjectInfo(
                Paths.get("tstData", "InlineCompletionProject")
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
                Thread.sleep(2000)

                ideFrame {
                    openFile("MathClass.java")
                    editor {
                        moveCaretToOffset(text.length - 2)

                        // Trigger completion
                        ui.keyboard {
                            pressing(KeyEvent.VK_ALT) {
                                key(KeyEvent.VK_C)
                            }
                        }
                        Thread.sleep(1000)

                        // Verify suggestion appeared
                        val hintExists = editor.getInlayModel().getInlineElementsInRange(0, text.length).isNotEmpty()
                        assertThat(hintExists).isTrue()

                        // Reject with Esc
                        ui.keyboard {
                            key(KeyEvent.VK_ESCAPE)
                        }

                        val hintGone = editor.getInlayModel().getInlineElementsInRange(0, text.length).isEmpty()
                        assertThat(hintGone).isTrue()
                    }
                }
            }
    }

    // Test Case 3: Manual trigger + discard
    @Test
    fun `test manual trigger with discard`() {
        val testCase = TestCase(
            IdeProductProvider.IC,
            LocalProjectInfo(
                Paths.get("tstData", "InlineCompletionProject")
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
                Thread.sleep(2000)
                var originalText: String? = null

                ideFrame {
                    openFile("MathClass.java")
                    editor {
                        originalText = text
                        moveCaretToOffset(text.length - 2)

                        // Trigger completion
                        ui.keyboard {
                            pressing(KeyEvent.VK_ALT) {
                                key(KeyEvent.VK_C)
                            }
                        }

                        // Verify suggestion shown
                        Thread.sleep(1000)
                        val hintExists = editor.getInlayModel().getInlineElementsInRange(0, text.length).isNotEmpty()
                        assertThat(hintExists).isTrue()

                        // Move cursor up to discard
                        goToLine(getCaretLine() - 1)

                        // Verify no suggestion shown
                        val hintGone = editor.getInlayModel().getInlineElementsInRange(0, text.length).isEmpty()
                        assertThat(hintGone).isTrue()
                    }
                }
            }
    }

    // Test Case 4: Auto trigger + accept
    @Test
    fun `test auto trigger with acceptance`() {
        val testCase = TestCase(
            IdeProductProvider.IC,
            LocalProjectInfo(
                Paths.get("tstData", "InlineCompletionProject")
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
                Thread.sleep(2000)
                var originalText: String? = null
                var afterSuggestion: String? = null

                ideFrame {
                    openFile("MathClass.java")
                    editor {
                        originalText = text
                        moveCaretToOffset(text.length - 2)

                        // Auto-trigger with Enter
                        ui.keyboard {
                            key(KeyEvent.VK_ENTER)
                        }
                        Thread.sleep(1000)

                        val hintExists = editor.getInlayModel().getInlineElementsInRange(0, text.length).isNotEmpty()
                        assertThat(hintExists).isTrue()

                        ui.keyboard {
                            key(KeyEvent.VK_TAB)
                        }
                        afterSuggestion = text
                    }
                }
                assertThat(afterSuggestion).isNotEqualTo(originalText)
            }
    }

    // Test Case 5: Auto trigger + reject
    @Test
    fun `test auto trigger with rejections`() {
        val testCase = TestCase(
            IdeProductProvider.IC,
            LocalProjectInfo(
                Paths.get("tstData", "InlineCompletionProject")
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
                Thread.sleep(2000)
                var originalText: String? = null

                ideFrame {
                    openFile("MathClass.java")
                    editor {
                        originalText = text
                        moveCaretToOffset(text.length - 2)

                        // Auto-trigger with Enter
                        ui.keyboard {
                            key(KeyEvent.VK_ENTER)
                        }
                        Thread.sleep(1000)

                        val hintExists = editor.getInlayModel().getInlineElementsInRange(0, text.length).isNotEmpty()
                        assertThat(hintExists).isTrue()

                        // Reject with Esc
                        ui.keyboard {
                            key(KeyEvent.VK_ESCAPE)
                        }

                        val hintGone = editor.getInlayModel().getInlineElementsInRange(0, text.length).isEmpty()
                        assertThat(hintGone).isTrue()
                    }
                }
            }
    }

    // Test Case 6: Auto trigger + reject
    @Test
    fun `test auto trigger with discards`() {
        val testCase = TestCase(
            IdeProductProvider.IC,
            LocalProjectInfo(
                Paths.get("tstData", "InlineCompletionProject")
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
                Thread.sleep(2000)
                var originalText: String? = null

                ideFrame {
                    openFile("MathClass.java")
                    editor {
                        originalText = text
                        moveCaretToOffset(text.length - 2)

                        // Auto-trigger with Enter
                        ui.keyboard {
                            key(KeyEvent.VK_ENTER)
                        }
                        Thread.sleep(1000)

                        val hintExists = editor.getInlayModel().getInlineElementsInRange(0, text.length).isNotEmpty()
                        assertThat(hintExists).isTrue()

                        // Move cursor up to discard
                        goToLine(getCaretLine() - 1)

                        // Verify suggestion disappeared
                        val hintGone = editor.getInlayModel().getInlineElementsInRange(0, text.length).isEmpty()
                        assertThat(hintGone).isTrue()
                    }
                }
            }
    }
    // Test Case 7: Suggestion Navigation
    @Test
    fun `test suggestion navigation`() {
        val testCase = TestCase(
            IdeProductProvider.IC,
            LocalProjectInfo(
                Paths.get("tstData", "InlineCompletionProject")
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
                Thread.sleep(2000)

                ideFrame {
                    openFile("MathClass.java")
                    editor {
                        moveCaretToOffset(text.length - 2)

                        // Trigger completion
                        ui.keyboard {
                            pressing(KeyEvent.VK_ALT) {
                                key(KeyEvent.VK_C)
                            }
                        }
                        Thread.sleep(1000)

                        // Get initial suggestion
                        val initialHints = editor.getInlayModel().getInlineElementsInRange(0, text.length)
                        assertThat(initialHints).isNotEmpty()

                        // Navigate right
                        ui.keyboard {
                            pressing(KeyEvent.VK_ALT) {
                                key(KeyEvent.VK_CLOSE_BRACKET)
                            }
                        }

                        // Verify suggestion changed
                        val newHints = editor.getInlayModel().getInlineElementsInRange(0, text.length)
                        assertThat(newHints).isNotEqualTo(initialHints)
                    }
                }
            }
    }

    // Test Case 8: Unsupported language
    @Test
    fun `test completion in unsupported file type`() {
        val testCase = TestCase(
            IdeProductProvider.IC,
            LocalProjectInfo(
                Paths.get("tstData", "InlineCompletionProject")
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
                Thread.sleep(2000)

                ideFrame {
                    openFile("nonsense.xyz")
                    editor {
                        // Try manual trigger
                        moveCaretToOffset(text.length - 2)
                        ui.keyboard {
                            pressing(KeyEvent.VK_ALT) {
                                key(KeyEvent.VK_C)
                            }
                        }
                        Thread.sleep(1000)

                        // Verify no suggestion appeared
                        val hintExists = editor.getInlayModel().getInlineElementsInRange(0, text.length).isNotEmpty()
                        assertThat(hintExists).isFalse()

                        // Try auto trigger
                        ui.keyboard {
                            key(KeyEvent.VK_ENTER)
                        }
                        Thread.sleep(1000)

                        // Verify still no suggestion
                        val hintExistsAfterAuto = editor.getInlayModel().getInlineElementsInRange(0, text.length).isNotEmpty()
                        assertThat(hintExistsAfterAuto).isFalse()
                    }
                }
            }
    }

    // Test Case 9: Typeahead
    @Test
    fun `test typeahead behavior`() {
        val testCase = TestCase(
            IdeProductProvider.IC,
            LocalProjectInfo(
                Paths.get("tstData", "InlineCompletionProject")
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
                Thread.sleep(2000)

                ideFrame {
                    openFile("MathClass.java")
                    editor {
                        moveCaretToOffset(text.length - 2)

                        // Trigger completion
                        ui.keyboard {
                            pressing(KeyEvent.VK_ALT) {
                                key(KeyEvent.VK_C)
                            }
                        }
                        Thread.sleep(1000)

                        // Verify suggestion appeared
                        val initialHintExists = editor.getInlayModel().getInlineElementsInRange(0, text.length).isNotEmpty()
                        assertThat(initialHintExists).isTrue()

                        // Type matching prefix
                        ui.keyboard {
                            key(KeyEvent.VK_P)
                            key(KeyEvent.VK_U)
                            key(KeyEvent.VK_B)
                        }

                        // Verify suggestion still exists
                        val hintExistsAfterTyping = editor.getInlayModel().getInlineElementsInRange(0, text.length).isNotEmpty()
                        assertThat(hintExistsAfterTyping).isTrue()
                    }
                }
            }
    }
}
