// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.uitests.inlineTests

import com.intellij.driver.sdk.openFile
import com.intellij.driver.sdk.step
import com.intellij.driver.sdk.ui.components.common.editor
import com.intellij.driver.sdk.ui.components.common.ideFrame
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
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.kodein.di.DI
import org.kodein.di.bindSingleton
import software.aws.toolkits.jetbrains.uitests.TestCIServer
import software.aws.toolkits.jetbrains.uitests.useExistingConnectionForTest
import java.awt.event.KeyEvent
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardOpenOption

class AmazonQInlineCompletionE2ETest {
    private val originalContent = """public class MathClass {
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
        resetTestFile()
    }

    @BeforeEach
    fun resetTestFile() {
        val path = Paths.get("tstData", "inlineCompletionProject", "MathClass.java")

        Files.createDirectories(path.parent)
        Files.write(
            path,
            originalContent.toByteArray(),
            StandardOpenOption.CREATE,
            StandardOpenOption.TRUNCATE_EXISTING
        )
    }

    @Test
    fun `test inline completion functionality`() {
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

                step("Test manual invoke and accept") {
                    var originalText: String? = null
                    var afterSuggestion: String? = null

                    ideFrame {
                        openFile("MathClass.java")
                        editor {
                            originalText = text
                            moveCaretToOffset(text.length - 2)

                            ui.keyboard {
                                pressing(KeyEvent.VK_ALT) {
                                    key(KeyEvent.VK_C)
                                }
                            }
                            Thread.sleep(1000)

                            val hintExists = editor.getInlayModel().getInlineElementsInRange(0, text.length).isNotEmpty()
                            assertThat(hintExists).isTrue()

                            ui.keyboard {
                                key(KeyEvent.VK_TAB)
                            }
                            afterSuggestion = text
                            text = originalContent
                        }
                    }
                    assertThat(afterSuggestion).isNotEqualTo(originalText)
                }

                step("Test manual trigger with rejection") {
                    ideFrame {
                        openFile("MathClass.java")
                        editor {
                            moveCaretToOffset(text.length - 2)

                            ui.keyboard {
                                pressing(KeyEvent.VK_ALT) {
                                    key(KeyEvent.VK_C)
                                }
                            }
                            Thread.sleep(1000)

                            val hintExists = editor.getInlayModel().getInlineElementsInRange(0, text.length).isNotEmpty()
                            assertThat(hintExists).isTrue()

                            ui.keyboard {
                                key(KeyEvent.VK_ESCAPE)
                            }

                            val hintGone = editor.getInlayModel().getInlineElementsInRange(0, text.length).isEmpty()
                            assertThat(hintGone).isTrue()
                            text = originalContent
                        }
                    }
                }

                step("Test manual trigger with discard") {
                    ideFrame {
                        openFile("MathClass.java")
                        editor {
                            moveCaretToOffset(text.length - 2)

                            ui.keyboard {
                                pressing(KeyEvent.VK_ALT) {
                                    key(KeyEvent.VK_C)
                                }
                            }
                            Thread.sleep(1000)

                            val hintExists = editor.getInlayModel().getInlineElementsInRange(0, text.length).isNotEmpty()
                            assertThat(hintExists).isTrue()

                            goToLine(getCaretLine() - 1)

                            val hintGone = editor.getInlayModel().getInlineElementsInRange(0, text.length).isEmpty()
                            assertThat(hintGone).isTrue()
                            text = originalContent
                        }
                    }
                }

                step("Test auto trigger with acceptance") {
                    var originalText: String? = null
                    var afterSuggestion: String? = null

                    ideFrame {
                        openFile("MathClass.java")
                        editor {
                            originalText = text
                            moveCaretToOffset(text.length - 2)

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
                            text = originalContent
                        }
                    }
                    assertThat(afterSuggestion).isNotEqualTo(originalText)
                }

                step("Test auto trigger with rejection") {
                    ideFrame {
                        openFile("MathClass.java")
                        editor {
                            moveCaretToOffset(text.length - 2)

                            ui.keyboard {
                                key(KeyEvent.VK_ENTER)
                            }
                            Thread.sleep(1000)

                            val hintExists = editor.getInlayModel().getInlineElementsInRange(0, text.length).isNotEmpty()
                            assertThat(hintExists).isTrue()

                            ui.keyboard {
                                key(KeyEvent.VK_ESCAPE)
                            }

                            val hintGone = editor.getInlayModel().getInlineElementsInRange(0, text.length).isEmpty()
                            assertThat(hintGone).isTrue()
                            text = originalContent
                        }
                    }
                }

                step("Test auto trigger with discard") {
                    ideFrame {
                        openFile("MathClass.java")
                        editor {
                            moveCaretToOffset(text.length - 2)

                            ui.keyboard {
                                key(KeyEvent.VK_ENTER)
                            }
                            Thread.sleep(1000)

                            val hintExists = editor.getInlayModel().getInlineElementsInRange(0, text.length).isNotEmpty()
                            assertThat(hintExists).isTrue()

                            goToLine(getCaretLine() - 1)

                            val hintGone = editor.getInlayModel().getInlineElementsInRange(0, text.length).isEmpty()
                            assertThat(hintGone).isTrue()
                            text = originalContent
                        }
                    }
                }

                step("Test suggestion navigation") {
                    ideFrame {
                        openFile("MathClass.java")
                        editor {
                            moveCaretToOffset(text.length - 2)

                            ui.keyboard {
                                pressing(KeyEvent.VK_ALT) {
                                    key(KeyEvent.VK_C)
                                }
                            }
                            Thread.sleep(1000)

                            val initialHints = editor.getInlayModel().getInlineElementsInRange(0, text.length)
                            assertThat(initialHints).isNotEmpty()

                            ui.keyboard {
                                pressing(KeyEvent.VK_ALT) {
                                    key(KeyEvent.VK_CLOSE_BRACKET)
                                }
                            }

                            val newHints = editor.getInlayModel().getInlineElementsInRange(0, text.length)
                            assertThat(newHints).isNotEqualTo(initialHints)
                            text = originalContent
                        }
                    }
                }

                step("Test completion in unsupported file type") {
                    ideFrame {
                        openFile("nonsense.xyz")
                        editor {
                            moveCaretToOffset(text.length - 2)
                            ui.keyboard {
                                pressing(KeyEvent.VK_ALT) {
                                    key(KeyEvent.VK_C)
                                }
                            }
                            Thread.sleep(1000)

                            val hintExists = editor.getInlayModel().getInlineElementsInRange(0, text.length).isNotEmpty()
                            assertThat(hintExists).isFalse()

                            ui.keyboard {
                                key(KeyEvent.VK_ENTER)
                            }
                            Thread.sleep(1000)

                            val hintExistsAfterAuto = editor.getInlayModel().getInlineElementsInRange(0, text.length).isNotEmpty()
                            assertThat(hintExistsAfterAuto).isFalse()
                            text = originalContent
                        }
                    }
                }

                step("Test typeahead behavior") {
                    ideFrame {
                        openFile("MathClass.java")
                        editor {
                            moveCaretToOffset(text.length - 2)

                            ui.keyboard {
                                pressing(KeyEvent.VK_ALT) {
                                    key(KeyEvent.VK_C)
                                }
                            }
                            Thread.sleep(1000)

                            val initialHintExists = editor.getInlayModel().getInlineElementsInRange(0, text.length).isNotEmpty()
                            assertThat(initialHintExists).isTrue()

                            ui.keyboard {
                                key(KeyEvent.VK_P)
                                key(KeyEvent.VK_U)
                                key(KeyEvent.VK_B)
                            }

                            val hintExistsAfterTyping = editor.getInlayModel().getInlineElementsInRange(0, text.length).isNotEmpty()
                            assertThat(hintExistsAfterTyping).isTrue()
                            text = originalContent
                        }
                    }
                }
            }
    }
}
