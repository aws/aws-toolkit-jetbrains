// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.uitests.tests

import com.intellij.remoterobot.fixtures.ComponentFixture
import com.intellij.remoterobot.search.locators.byXpath
import com.intellij.remoterobot.stepsProcessing.log
import com.intellij.remoterobot.stepsProcessing.step
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.io.TempDir
import software.amazon.awssdk.services.sqs.SqsClient
import software.amazon.awssdk.services.sqs.model.QueueDoesNotExistException
import software.aws.toolkits.core.utils.Waiters.waitUntilBlocking
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
        var client = SqsClient.create()
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
                fillSingleTextField(queueName)
                find<ComponentFixture>(byXpath("//div[@accessiblename='Standard']")).click()
                pressCreate()
            }
            step("Create FIFO queue $fifoQueueName") {
                awsExplorer {
                    openExplorerActionMenu(sqsNodeLabel)
                }
                find<ComponentFixture>(byXpath("//div[@text='$createQueueText']")).click()
                fillSingleTextField(queueName)
                find<ComponentFixture>(byXpath("//div[@accessiblename='FIFO']")).click()
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
                client.waitForDeletion(queueName)
            }
            step("Delete queue $fifoQueueName") {
                showAwsExplorer()
                awsExplorer {
                    openExplorerActionMenu(sqsNodeLabel, fifoQueueName)
                }
                findAndClick("//div[@text='$deleteQueueText']")
                fillSingleTextField(fifoQueueName)
                pressOk()
                client.waitForDeletion(fifoQueueName)
            }
        }
    }

    @AfterAll
    fun cleanup() {
        // Make sure the two queues are deleted, and if not, delete them
        var client: SqsClient? = null
        try {
            client = SqsClient.create()
            client.verifyDeleted(queueName)
            client.verifyDeleted(fifoQueueName)
        } catch (e: Exception) {
            log.error("Unable to verify the queues were removed", e)
        } finally {
            client?.close()
        }
    }

    private fun SqsClient.verifyDeleted(queueName: String) {
        val queueUrl = try {
            getQueueUrl { it.queueName(queueName) }.queueUrl()
        } catch (e: QueueDoesNotExistException) {
            log.info("Queue is deleted!")
            return
        } catch (e: Exception) {
            log.error("Get queue URL returned an error, cannot attempt deletion again", e)
            return
        }
        log.info("Deleting $queueUrl")
        try {
            deleteQueue { it.queueUrl(queueUrl) }
        } catch (e: Exception) {
            log.info("Trying to delete $queueUrl threw an exception, it might not be deleted!", e)
            return
        }

        waitForDeletion(queueName)
    }

    private fun SqsClient.waitForDeletion(queueName: String) {
        try {
            waitUntilBlocking(exceptionsToStopOn = setOf(QueueDoesNotExistException::class)) {
                getQueueUrl { it.queueName(queueName) }
            }
            log.info("Verified $queueName is deleted")
        } catch (e: Exception) {
            log.error("Unknown exception thrown by waitForDeletion", e)
        }
    }
}
