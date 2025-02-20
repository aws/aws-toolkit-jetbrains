// Copyright 2021 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.uitests

import com.intellij.driver.sdk.ui.components.textField
import com.intellij.driver.sdk.ui.ui
import com.intellij.driver.sdk.waitForProjectOpen
import com.intellij.ide.starter.driver.engine.runIdeWithDriver
import com.intellij.ide.starter.ide.IdeProductProvider
import com.intellij.ide.starter.models.TestCase
import com.intellij.ide.starter.project.LocalProjectInfo
import com.intellij.ide.starter.runner.Starter
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.io.TempDir
import org.slf4j.LoggerFactory
import software.aws.toolkits.jetbrains.uitests.utils.IdeStarterTestUtils
import software.aws.toolkits.jetbrains.uitests.utils.IdeStarterTestUtils.findAndClick
import java.io.File
import java.nio.file.Path
import java.util.Locale

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SamTemplateProjectWizardTest {
    private val logger = LoggerFactory.getLogger(javaClass)

    @TempDir
    lateinit var tempDir: Path

    private var samCliConfigured = false

    @BeforeAll
    fun setup() {
        val samPath = System.getenv("SAM_CLI_EXEC")
        if (samPath.isNullOrEmpty()) {
            logger.warn("No custom SAM set, skipping setup")
            return
        }
    }

    @Test
    fun createSamApp() {
        if (!samCliConfigured) {
            IdeStarterTestUtils.setupSamCliWithStarter(tempDir)
            samCliConfigured = true
        }

        val testCase = TestCase(
            IdeProductProvider.IC,
            LocalProjectInfo(tempDir)
        )

        Starter.newContext("createSamApp", testCase).apply {
            // Install required plugins
            System.getProperty("ui.test.plugins").split(File.pathSeparator).forEach { path ->
                pluginConfigurator.installPluginFromPath(Path.of(path))
            }

            updateGeneralSettings()
        }.runIdeWithDriver()
            .useDriverAndCloseIde {
                ui.findAndClick("//div[contains(@accessiblename, 'New Project')]")

                ui.findAndClick("//div[text='AWS']")

                ui.findAndClick("//div[text='AWS Serverless Application']")

                ui.findAndClick("//button[text='Next']")

                ui.textField("//div[@class='TextFieldWithBrowseButton']").text = tempDir.toAbsolutePath().toString()

                ui.findAndClick("//button[text='Create']")

                waitForProjectOpen()

                ui.textField("//div[@class='EditorTab' and contains(@text, 'README.md')]")?.let { editor ->
                    assertThat(editor).isNotNull()
                }

                ui.keyboard {
                    if (System.getProperty("os.name").lowercase(Locale.getDefault()).contains("mac")) {
                        hotKey(157, 59) // Cmd + ; for Mac
                    } else {
                        hotKey(17, 18, 16, 83) // Ctrl + Alt + Shift + S for Windows/Linux
                    }
                }

                ui.textField("//div[@class='JdkComboBox']")?.let { jdkCombo ->
                    assertThat(jdkCombo.text).startsWith("2")
                }

                ui.findAndClick("//button[text='OK']")
            }
    }
}
