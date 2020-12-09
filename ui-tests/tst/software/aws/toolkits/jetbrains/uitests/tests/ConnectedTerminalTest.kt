// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.uitests.tests

import com.intellij.remoterobot.stepsProcessing.step
import com.intellij.remoterobot.utils.keyboard
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.io.TempDir
import software.aws.toolkits.jetbrains.uitests.CoreTest
import software.aws.toolkits.jetbrains.uitests.extensions.uiTest
import software.aws.toolkits.jetbrains.uitests.fixtures.findAndClick
import software.aws.toolkits.jetbrains.uitests.fixtures.findByXpath
import software.aws.toolkits.jetbrains.uitests.fixtures.idea
import software.aws.toolkits.jetbrains.uitests.fixtures.welcomeFrame
import java.nio.file.Path
import java.util.function.Predicate

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ConnectedTerminalTest {

    @TempDir
    lateinit var tempDir: Path

    @Test
    @CoreTest
    fun `can open a terminal from explorer`() = uiTest {
        try {
            welcomeFrame {
                openFolder(tempDir)
            }
        } catch (e: Exception) {
            println(e)
        }
        idea {
            waitForBackgroundTasks()
            showAwsExplorer()
        }

        idea {
            step("click terminal button") {
                findAndClick("//div[@accessiblename='Start AWS Terminal' and @class='ActionButton']")
            }
            step("assert terminal shown") {
                val connection = step("find current connection") {
                    findText(Predicate { it.text.startsWith("AWS: ") }).text.substringAfter("AWS: ")
                }
                step("confirm terminal tab showing with connection $connection") {
                    findByXpath("//div[@accessiblename='$connection' and @class='ContentTabLabel' and @text='$connection']")
                }
                val terminal = step("find terminal window") {
                    findByXpath("//div[@class='ShellTerminalWidget' and @name='terminal']")
                }
                step("click in terminal") {
                    terminal.click()
                }
                step("echo out region") {
                    keyboard {
                        enterText("echo \$AWS_REGION")
                        enter()
                    }
                }

                assertThat(terminal.findAllText().joinToString(separator = "") { it.text }).contains(connection.substringAfter("@"))
            }
        }
    }
}
