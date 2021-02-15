// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.uitests.tests

import com.intellij.remoterobot.fixtures.ComboBoxFixture
import com.intellij.remoterobot.fixtures.ContainerFixture
import com.intellij.remoterobot.fixtures.JListFixture
import com.intellij.remoterobot.search.locators.byXpath
import com.intellij.remoterobot.stepsProcessing.step
import com.intellij.remoterobot.utils.keyboard
import org.apache.commons.io.FileUtils
import org.assertj.core.api.Assertions.assertThat
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
import java.time.Duration

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SamRunConfigTest {
    private val dataPath = Paths.get(System.getProperty("testDataPath")).resolve("samProjects/zip/java11")
    private val input = """
        {"data": "${dataPath.hashCode()}"}
    """.trimIndent()

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
                findAndClick("//div[@accessiblename='Add Configuration...']")
                val dialog = step("Add a run configuration") {
                    val dialog = find<DialogFixture>(DialogFixture.byTitleContains("Run"), Duration.ofSeconds(5))
                    dialog.findAndClick("//div[@accessiblename='Add New Configuration']")
                    find<JTreeFixture>(byXpath("//div[@accessiblename='WizardTree' and @class='MyTree']")).clickPath("AWS Lambda", "Local")
                    dialog
                }
                step("Populate run configuration") {
                    step("Set up function from template") {
                        findAndClick("//div[@text='From template']")
                        findAndClick("//div[@class='Wrapper']//div[@class='TextFieldWithBrowseButton']")
                        keyboard { enterText(tempDir.resolve("template.yaml").toAbsolutePath().toString()) }
                    }
                    step("Assert validation works by checking the error") {
                        assertThat(dialog.findAllText()).anySatisfy { assertThat(it.text).contains("Must specify an input") }
                    }
                    step("Enter text") {
                        findAndClick("//div[@class='MyEditorTextField']")
                        keyboard { enterText(input) }
                    }
                    pressOk()
                }
                step("Validate run configuration was saved and loads properly") {
                    step("Reopen the run configuration") {
                        findAndClick("//div[@accessiblename='[Local] SomeFunction']")
                        find<JListFixture>(byXpath("//div[@class='MyList']")).selectItem("Edit Configurations...")
                    }
                    step("Assert the same function is selected") {
                        assertThat(functionModel().selectedText()).isEqualTo("SomeFunction")
                    }
                    step("Assert the run configuration has no errors") {
                        assertThat(dialog.findAllText()).noneSatisfy { assertThat(it.text).contains("Error") }
                    }
                    // TODO the field is a JTextComponent so findAllText does not work and we don't have a JComponent
                    // step("Assert the input is the same") {
                    //    assertThat(dialog.findAllText()).anySatisfy { assertThat(it.text).isEqualTo(input) }
                    // }
                }
            }
        }
    }

    private fun ContainerFixture.functionModel(): ComboBoxFixture =
        find(byXpath("//div[@class='TextFieldWithBrowseButton']/following-sibling::div[@class='ComboBox']"))
}
