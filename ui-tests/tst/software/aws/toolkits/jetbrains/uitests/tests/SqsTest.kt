// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.uitests.tests

import com.intellij.remoterobot.fixtures.ComponentFixture
import com.intellij.remoterobot.search.locators.byXpath
import com.intellij.remoterobot.stepsProcessing.step
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.io.TempDir
import software.aws.toolkits.jetbrains.uitests.CoreTest
import software.aws.toolkits.jetbrains.uitests.extensions.uiTest
import software.aws.toolkits.jetbrains.uitests.fixtures.awsExplorer
import software.aws.toolkits.jetbrains.uitests.fixtures.fillSingleTextField
import software.aws.toolkits.jetbrains.uitests.fixtures.findAndClick
import software.aws.toolkits.jetbrains.uitests.fixtures.idea
import software.aws.toolkits.jetbrains.uitests.fixtures.pressCreate
import software.aws.toolkits.jetbrains.uitests.fixtures.pressOk
import software.aws.toolkits.jetbrains.uitests.fixtures.welcomeFrame
import java.nio.file.Path
import java.util.UUID

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SqsTest {
    @TempDir
    lateinit var tempDir: Path

    private val sqsNodeLabel = "SQS"
    private val createQueueText = "Create Queue"
    private val deleteQueueText = "Delete Queue"

    private val queueName = "uitest-${UUID.randomUUID()}"
    private val fifoQueueName = "$queueName.fifo"

    @Test
    @CoreTest
    fun testSqs() = uiTest {
        welcomeFrame {
            openFolder(tempDir)
        }
        idea {
            waitForBackgroundTasks()
            showAwsExplorer()
        }
        idea {
            step("Create queue $queueName") {
                awsExplorer {
                    openExplorerActionMenu(sqsNodeLabel)
                }
                find<ComponentFixture>(byXpath("//div[@text='$createQueueText']")).click()
                find<ComponentFixture>(byXpath("//div[@accessiblename='Standard']")).click()
                fillSingleTextField(queueName)
                pressCreate()
            }
            step("Create FIFO queue $fifoQueueName") {
                awsExplorer {
                    openExplorerActionMenu(sqsNodeLabel)
                }
                find<ComponentFixture>(byXpath("//div[@text='$createQueueText']")).click()
                find<ComponentFixture>(byXpath("//div[@accessiblename='FIFO']")).click()
                fillSingleTextField(queueName)
                pressCreate()
            }
            step("Delete queue $queueName") {
                showAwsExplorer()
                awsExplorer {
                    openExplorerActionMenu(sqsNodeLabel, queueName)
                }
                findAndClick("//div[@text='$deleteQueueText']")
                fillSingleTextField(queueName)
                pressOk()
            }
            step("Delete queue $fifoQueueName") {
                showAwsExplorer()
                awsExplorer {
                    openExplorerActionMenu(sqsNodeLabel, fifoQueueName)
                }
                findAndClick("//div[@text='$deleteQueueText']")
                fillSingleTextField(fifoQueueName)
                pressOk()
            }
        }
    }

    @AfterAll
    fun cleanup() {
        // Make sure the two queues are deleted, and if not, delete them

    }
}
