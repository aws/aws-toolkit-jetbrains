// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.uitests.tests

import com.intellij.remoterobot.search.locators.byXpath
import com.intellij.remoterobot.stepsProcessing.step
import org.apache.commons.io.FileUtils
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.io.TempDir
import software.aws.toolkits.jetbrains.uitests.CoreTest
import software.aws.toolkits.jetbrains.uitests.extensions.uiTest
import software.aws.toolkits.jetbrains.uitests.fixtures.editorTab
import software.aws.toolkits.jetbrains.uitests.fixtures.idea
import software.aws.toolkits.jetbrains.uitests.fixtures.newProjectWizard
import software.aws.toolkits.jetbrains.uitests.fixtures.projectStructureDialog
import software.aws.toolkits.jetbrains.uitests.fixtures.welcomeFrame
import software.aws.toolkits.jetbrains.uitests.utils.setupSamCli
import java.nio.file.Path
import java.nio.file.Paths

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SamRunConfigTest {
    private val dataPath = Paths.get(System.getProperty("testDataPath")).resolve("samProjects/zip/java11")

    @TempDir
    lateinit var tempDir: Path

    @BeforeAll
    fun setup() {
        // copy our test data to the temporary folder so we can edit it
        FileUtils.copyDirectory(dataPath.toFile(), tempDir.toFile())
        setupSamCli()
    }

    @Test
    @CoreTest
    fun samRunConfig() {
        uiTest {
            welcomeFrame {
                openFolder(tempDir)
            }

            idea {
                waitForBackgroundTasks()

                step("Validate Readme is opened") {
                    editorTab("README.md")
                }

                step("Validate project structure") {
                    projectStructureDialog {
                        val fixture = comboBox(byXpath("//div[@class='JdkComboBox']"))
                        // TODO set based on Runtime
                        assertThat(fixture.selectedText()).isEqualTo("11")
                    }
                }
            }
        }
    }
}
