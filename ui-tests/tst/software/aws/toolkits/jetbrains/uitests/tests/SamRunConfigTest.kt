// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.uitests.tests

import com.intellij.remoterobot.search.locators.byXpath
import com.intellij.remoterobot.stepsProcessing.step
import com.intellij.remoterobot.utils.keyboard
import org.apache.commons.io.FileUtils
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.io.TempDir
import software.aws.toolkits.jetbrains.uitests.CoreTest
import software.aws.toolkits.jetbrains.uitests.extensions.uiTest
import software.aws.toolkits.jetbrains.uitests.fixtures.DialogFixture
import software.aws.toolkits.jetbrains.uitests.fixtures.JTreeFixture
import software.aws.toolkits.jetbrains.uitests.fixtures.findAndClick
import software.aws.toolkits.jetbrains.uitests.fixtures.idea
import software.aws.toolkits.jetbrains.uitests.fixtures.pressOk
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
        setupSamCli()
    }

    @Test
    @CoreTest
    fun samRunConfig() {
        // copy our test data to the temporary folder so we can edit it
        FileUtils.copyDirectory(dataPath.toFile(), tempDir.toFile())
        uiTest {
            welcomeFrame {
                openFolder(tempDir)
            }

            idea {
                waitForBackgroundTasks()
                step("Add a run configuration") {
                    findAndClick("//div[@accessiblename='Add Configuration...']")
                    val dialog = find<DialogFixture>(DialogFixture.byTitleContains("Run"))
                    dialog.findAndClick("//div[@accessiblename='Add New Configuration']")
                    find<JTreeFixture>(byXpath("//div[@accessiblename='WizardTree' and @class='MyTree']")).clickPath("AWS Lambda", "Local")
                }
                step("Populate run configuration") {
                    findAndClick("//div[@text='From template']")
                    findAndClick("//div[@class='Wrapper']//div[@class='TextFieldWithBrowseButton']")
                    keyboard { enterText(tempDir.resolve("template.yml").toAbsolutePath().toString()) }
                    findAndClick("//div[@class='MyEditorTextField']")
                    keyboard { enterText("{}") }
                    pressOk()
                }
            }
        }
    }
}
