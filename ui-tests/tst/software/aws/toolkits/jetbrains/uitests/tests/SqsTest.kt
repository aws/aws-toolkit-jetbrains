// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.uitests.tests

import com.intellij.remoterobot.RemoteRobot
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
import software.aws.toolkits.jetbrains.uitests.fixtures.JTreeFixture
import software.aws.toolkits.jetbrains.uitests.fixtures.awsExplorer
import software.aws.toolkits.jetbrains.uitests.fixtures.fillAllTextFields
import software.aws.toolkits.jetbrains.uitests.fixtures.fillSingleTextField
import software.aws.toolkits.jetbrains.uitests.fixtures.findAndClick
import software.aws.toolkits.jetbrains.uitests.fixtures.findByXpath
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
    private val purgeQueueText = "Purge Queue"

    private val queueName = "uitest-${UUID.randomUUID()}"
    private val fifoQueueName = "$queueName.fifo"

    @Test
    @CoreTest
    fun testSqs() = uiTest {
        val client = SqsClient.create()
        welcomeFrame {
            openFolder(tempDir)
        }
        idea {
            waitForBackgroundTasks()
            showAwsExplorer()
        }
        idea {
            step("Create queues") {
                step("Create queue $queueName") {
                    awsExplorer {
                        openExplorerActionMenu(sqsNodeLabel)
                    }
                    find<ComponentFixture>(byXpath("//div[@text='$createQueueText']")).click()
                    fillSingleTextField(queueName)
                    find<ComponentFixture>(byXpath("//div[@accessiblename='Standard']")).click()
                    pressCreate()
                    client.waitForCreation(queueName)
                }
                step("Create FIFO queue $fifoQueueName") {
                    awsExplorer {
                        openExplorerActionMenu(sqsNodeLabel)
                    }
                    find<ComponentFixture>(byXpath("//div[@text='$createQueueText']")).click()
                    fillSingleTextField(queueName)
                    find<ComponentFixture>(byXpath("//div[@accessiblename='FIFO']")).click()
                    pressCreate()
                    client.waitForCreation(fifoQueueName)
                }
            }
            step("Expand SQS node") { awsExplorer { expandExplorerNode(sqsNodeLabel) } }
            step("Standard queue") {
                openSendMessagePane(queueName)
                step("Send a message and validate it is sent") {
                    fillSingleTextField("message")
                    findAndClick("//div[@text='Send']")
                    // Make sure it shows a sent message
                    findByXpath("//div[contains(@accessiblename, 'Sent message ID')]")
                }
                step("pack the queue full of messages so poll will be garunteed to work") {
                    (1..10).forEach {
                        fillSingleTextField("bmessage$it")
                        findAndClick("//div[@text='Send']")
                    }
                }
                openPollMessagePane(queueName)
                step("poll for messages") {
                    findAndClick("//div[@accessiblename='Poll for Messages' and @class='JButton']")
                    // Wait for the table to be populated (Fast for small tables)
                    Thread.sleep(1000)
                    find<JTreeFixture>(byXpath("//div[@class='TableView']")).findAllText().any { it.text.contains("bmessage") }
                }
            }
            step("FIFO queue") {
                openSendMessagePane(fifoQueueName)
                step("Send a message and validate it is sent") {
                    fillAllTextFields("message")
                    findAndClick("//div[@text='Send']")
                    // Make sure it shows a sent message
                    findByXpath("//div[contains(@accessiblename, 'Sent message ID')]")
                }
            }
            step("Purge queue") {
                awsExplorer {
                    openExplorerActionMenu(sqsNodeLabel, queueName)
                    findAndClick("//div[@text='$purgeQueueText']")
                }
                validateNotificationIsShown("Started purging queue")
                awsExplorer {
                    openExplorerActionMenu(sqsNodeLabel, queueName)
                    findAndClick("//div[@text='$purgeQueueText']")
                }
                validateNotificationIsShown("Purge queue request already in progress for queue")
            }
            step("Delete queues") {
                step("Delete queue $queueName") {
                    awsExplorer {
                        openExplorerActionMenu(sqsNodeLabel, queueName)
                    }
                    findAndClick("//div[@text='$deleteQueueText']")
                    fillSingleTextField(queueName)
                    pressOk()
                    client.waitForDeletion(queueName)
                }
                step("Delete queue $fifoQueueName") {
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
    }

    @AfterAll
    // Make sure the two queues are deleted, and if not, delete them
    fun cleanup() {
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

    private fun RemoteRobot.openSendMessagePane(queueName: String) = step("Open send message pane") {
        awsExplorer {
            openExplorerActionMenu(sqsNodeLabel, queueName)
            findAndClick("//div[@accessiblename='Send a Message']")
        }
    }

    private fun RemoteRobot.openPollMessagePane(queueName: String) = step("Open poll message pane") {
        awsExplorer {
            openExplorerActionMenu(sqsNodeLabel, queueName)
            findAndClick("//div[@accessiblename='Poll for Messages']")
        }
    }

    private fun SqsClient.verifyDeleted(queueName: String) {
        val queueUrl = try {
            getQueueUrl { it.queueName(queueName) }.queueUrl()
        } catch (e: QueueDoesNotExistException) {
            log.info("Queue $queueName is deleted")
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

    private fun SqsClient.waitForCreation(queueName: String) {
        try {
            waitUntilBlocking(exceptionsToIgnore = setOf(QueueDoesNotExistException::class)) {
                getQueueUrl { it.queueName(queueName) }
            }
            log.info("Verified $queueName is created")
        } catch (e: Exception) {
            log.error("Unknown exception thrown by waitForDeletion", e)
        }
    }
}
