// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.uitests.tests

import com.intellij.remoterobot.fixtures.ComboBoxFixture
import com.intellij.remoterobot.fixtures.ComponentFixture
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
    private val input = "{}"

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
                // JB can't decide if they want ellipsis or not
                try {
                    menuBar.select("Run", "Edit Configurations...")
                } catch (e: Exception) {
                    findAndClick("//div[@class='IdeRootPane']")
                    menuBar.select("Run", "Edit Configurations…")
                }
                step("Create and populate template based run configuration") {
                    addRunConfig()
                    step("Populate run configuration") {
                        step("Set up function from template") {
                            findAndClick("//div[@text='From template']")
                            findAndClick("//div[@class='Wrapper']//div[@class='TextFieldWithBrowseButton']")
                            keyboard { enterText(tempDir.resolve("template.yaml").toAbsolutePath().toString()) }
                        }
                        step("Assert validation works by checking the error") {
                            assertThat(findRunDialog().findAllText()).anySatisfy { assertThat(it.text).contains("Must specify an input") }
                        }
                        step("Enter text") {
                            findAndClick("//div[@class='MyEditorTextField']")
                            keyboard { enterText(input) }
                        }
                        pressOk()
                    }
                }
                step("Validate template run configuration was saved and loads properly") {
                    step("Reopen the run configuration") {
                        findAndClick("//div[@accessiblename='[Local] SomeFunction']")
                        find<JListFixture>(byXpath("//div[@class='MyList']")).clickItem("Edit Configurations", fullMatch = false)
                    }
                    step("Assert the same function is selected") {
                        assertThat(functionModel().selectedText()).isEqualTo("SomeFunction")
                    }
                    step("Assert the run configuration has no errors") {
                        assertThat(findRunDialog().findAllText()).noneSatisfy { assertThat(it.text).contains("Error") }
                    }
                    step("Assert the input is the same") {
                        // As this is a JTextField we don't have a fixture for it. But, we can extract the data by
                        // joining all the text it has into a string
                        val fixture = findRunDialog().find<ContainerFixture>(byXpath("//div[@class='MyEditorTextField']"))
                        assertThat(fixture.findAllText().joinToString("") { it.text }).isEqualTo(input)
                    }
                    pressOk()
                }
                step("Setup handler based run configuration") {
                    step("Reopen the run configuration menu") {
                        findAndClick("//div[@accessiblename='[Local] SomeFunction']")
                        find<JListFixture>(byXpath("//div[@class='MyList']")).clickItem("Edit Configurations", fullMatch = false)
                    }
                    addRunConfig()
                    find<ComboBoxFixture>(
                        byXpath("(//div[@text='Runtime:']/following-sibling::div[@class='ComboBox'])[1]"),
                        Duration.ofSeconds(10)
                    ).selectItem("java11")
                    findAndClick("//div[@class='HandlerPanel']")
                    keyboard { enterText("helloworld.App::handleRequest") }
                    findAndClick("//div[@class='MyEditorTextField']")
                    keyboard { enterText(input) }
                    pressOk()
                }
                step("Validate handler run configuration was saved and loads properly") {
                    step("Reopen the run configuration") {
                        findAndClick("//div[@accessiblename='[Local] App.handleRequest']")
                        find<JListFixture>(byXpath("//div[@class='MyList']")).clickItem("Edit Configurations", fullMatch = false)
                        waitForConfigurationLoad()
                    }
                    step("Assert the same handler is selected") {
                        val fixture = find<ContainerFixture>(byXpath("//div[@class='HandlerPanel']"))
                        assertThat(fixture.findAllText().joinToString("") { it.text }).isEqualTo("helloworld.App::handleRequest")
                    }
                    // We might want to assert no errors here in the future. However, since we do not import the project, we don't
                    // index it, so we can't find the handler. We are not testing that here (that is tested in other tests), so
                    // it would probably not be worth testing in the UI test as well.
                }
            }
        }
    }

    private fun ContainerFixture.waitForConfigurationLoad() = find<ComponentFixture>(byXpath("//div[@text='Configuration']"), Duration.ofSeconds(10))

    private fun ContainerFixture.functionModel(): ComboBoxFixture {
        waitForConfigurationLoad()
        return find(byXpath("//div[@class='TextFieldWithBrowseButton']/following-sibling::div[@class='ComboBox']"))
    }

    private fun ContainerFixture.findRunDialog() = find<DialogFixture>(DialogFixture.byTitleContains("Run"), Duration.ofSeconds(10))

    private fun ContainerFixture.addRunConfig() {
        step("Add a local run configuration") {
            findRunDialog().findAndClick("//div[@accessiblename='Add New Configuration']")
            find<JTreeFixture>(byXpath("//div[@accessiblename='WizardTree' and @class='MyTree']")).clickPath("AWS Lambda", "Local")
            // wait for run config panel to render
            waitForConfigurationLoad()
        }
    }
}
