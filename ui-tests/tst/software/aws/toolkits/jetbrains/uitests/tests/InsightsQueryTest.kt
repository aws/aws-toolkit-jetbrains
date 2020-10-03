// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.uitests.tests

/* TODO uncomment to enable Logs Insights
import com.intellij.remoterobot.RemoteRobot
import com.intellij.remoterobot.fixtures.ComboBoxFixture
import com.intellij.remoterobot.fixtures.ComponentFixture
import com.intellij.remoterobot.fixtures.ContainerFixture
import com.intellij.remoterobot.fixtures.JLabelFixture
import com.intellij.remoterobot.fixtures.JTextFieldFixture
import com.intellij.remoterobot.search.locators.byXpath
import com.intellij.remoterobot.stepsProcessing.log
import com.intellij.remoterobot.stepsProcessing.step
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.io.TempDir
import software.amazon.awssdk.services.cloudwatchlogs.CloudWatchLogsClient
import software.amazon.awssdk.services.cloudwatchlogs.model.QueryStatus
import software.amazon.awssdk.services.cloudwatchlogs.model.ResourceNotFoundException
import software.aws.toolkits.jetbrains.services.cloudwatch.logs.insights.DEFAULT_INSIGHTS_QUERY_STRING
import software.aws.toolkits.jetbrains.uitests.CoreTest
import software.aws.toolkits.jetbrains.uitests.extensions.uiTest
import software.aws.toolkits.jetbrains.uitests.fixtures.JTreeFixture
import software.aws.toolkits.jetbrains.uitests.fixtures.awsExplorer
import software.aws.toolkits.jetbrains.uitests.fixtures.findAndClick
import software.aws.toolkits.jetbrains.uitests.fixtures.idea
import software.aws.toolkits.jetbrains.uitests.fixtures.rightClick
import software.aws.toolkits.jetbrains.uitests.fixtures.welcomeFrame
import java.lang.IllegalStateException
import java.nio.file.Path
import java.time.Duration
import java.time.Instant
import java.util.UUID
import java.util.function.Consumer

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class InsightsQueryTest {
    @TempDir
    lateinit var tempDir: Path

    private val cloudWatchExplorerLabel = "CloudWatch Logs"

    private val executeButtonText = "Execute"
    private val defaultRelativeTimeAmount = "10"
    private val testRelativeTimeAmount = "359"
    private val logGroupName = "uitest-${UUID.randomUUID()}"
    private val logStreamName1 = "uitest-${UUID.randomUUID()}"
    private val logStreamName2 = "uitest-${UUID.randomUUID()}"

    @BeforeAll
    fun setup() {
        val client = CloudWatchLogsClient.create()

        client.createLogGroup {
            it.logGroupName(logGroupName)
        }

        client.createLogStream {
            it.logGroupName(logGroupName)
            it.logStreamName(logStreamName1)
        }

        client.createLogStream {
            it.logGroupName(logGroupName)
            it.logStreamName(logStreamName2)
        }

        client.putLogEvents {
            it.logGroupName(logGroupName)
            it.logStreamName(logStreamName1)
            it.logEvents(
                Consumer { event ->
                    event.message("group1")
                    event.timestamp(Instant.now().toEpochMilli())
                }
            )
        }

        client.putLogEvents {
            it.logGroupName(logGroupName)
            it.logStreamName(logStreamName2)
            it.logEvents(
                Consumer { event ->
                    event.message("group2")
                    event.timestamp(Instant.now().toEpochMilli())
                }
            )
        }

        runBlocking {
            client.waitForResults(logGroupName)
        }
    }

    @Test
    @CoreTest
    fun testInsightsQuery() = uiTest {
        welcomeFrame {
            openFolder(tempDir)
        }
        idea {
            waitForBackgroundTasks()
            showAwsExplorer()
        }
        idea {
            step("Expand log groups node") {
                awsExplorer {
                    expandExplorerNode(cloudWatchExplorerLabel)
                }
            }
            step("Query with default settings returns results") {
                openInsightsQueryDialogFromExplorer(logGroupName)
                step("Execute") {
                    findAndClick("//div[@text='$executeButtonText']")
                }
                assertThat(find<JTreeFixture>(byXpath("//div[@class='TableView']"), Duration.ofSeconds(5)).findAllText()).anySatisfy {
                    assertThat(it.text).contains("group1")
                }
                assertThat(find<JTreeFixture>(byXpath("//div[@class='TableView']")).findAllText()).anySatisfy {
                    assertThat(it.text).contains("group2")
                }
            }
            step("Revising query from current results") {
                val currentTab = find<JLabelFixture>(byXpath("//div[@class='ContentTabLabel']"))
                val currentQueryId = find<JLabelFixture>(byXpath("//div[@class='ContentTabLabel']")).findAllText().first().text
                openInsightsQueryDialogFromResults()
                step("Change relative time values") {
                    find<JTextFieldFixture>(byXpath("//div[@class='JFormattedTextField' and @visible_text='$defaultRelativeTimeAmount']")).text =
                        testRelativeTimeAmount
                    find<ComboBoxFixture>(byXpath("//div[@class='ComboBox']")).selectItem("Hours")
                }
                step("Execute") {
                    findAndClick("//div[@text='$executeButtonText']")
                }
                step("Verify new result tab selected") {
                    // close the old one
                    currentTab.rightClick()
                    find<ComponentFixture>(byXpath("//div[@accessiblename='Close Tab' and @class='ActionMenuItem' and @text='Close Tab']")).click()

                    find<JLabelFixture>(byXpath("//div[@class='ContentTabLabel' and @visible_text!='$currentQueryId']")).click()
                }
                assertThat(find<JTreeFixture>(byXpath("//div[@class='TableView']"), Duration.ofSeconds(5)).findAllText()).anySatisfy {
                    assertThat(it.text).contains("group1")
                }
                assertThat(find<JTreeFixture>(byXpath("//div[@class='TableView']")).findAllText()).anySatisfy {
                    assertThat(it.text).contains("group2")
                }

                step("Verify new query settings have persisted") {
                    openInsightsQueryDialogFromResults()
                    find<JTextFieldFixture>(byXpath("//div[@class='JFormattedTextField' and @visible_text='$testRelativeTimeAmount']"))
                    assertThat(find<ComboBoxFixture>(byXpath("//div[@class='ComboBox']")).selectedText()).isEqualTo("Hours")
                }
            }
        }
    }

    @AfterAll
    fun cleanup() {
        try {
            val client = CloudWatchLogsClient.create()
            client.verifyDeletedLogGroup(logGroupName)
        } catch (e: Exception) {
            log.error("Unable to remove log groups", e)
        }
    }

    private fun RemoteRobot.openInsightsQueryDialogFromExplorer(groupName: String) = step("Open insights query dialog") {
        awsExplorer {
            openExplorerActionMenu(cloudWatchExplorerLabel, groupName)
        }
        find<ComponentFixture>(byXpath("//div[@accessiblename='Open Query Editor']")).click()
    }

    private fun ContainerFixture.openInsightsQueryDialogFromResults() = step("Open query editor") {
        findAndClick("//div[@text='Open Query Editor']")
    }

    private fun CloudWatchLogsClient.verifyDeletedLogGroup(logGroup: String) {
        try {
            deleteLogGroup {
                it.logGroupName(logGroupName)
            }
        } catch (e: ResourceNotFoundException) {
            log.info("Log group $logGroup did not exist")
            return
        } catch (e: Exception) {
            log.error("DeleteLogGroup returned an error, cannot attempt deletion again", e)
            return
        }
        log.info("Log group deleted: $logGroup")
    }

    private suspend fun CloudWatchLogsClient.waitForResults(logGroup: String) {
        // unfortunately logs are eventually consistent
        withTimeout(Duration.ofMinutes(5).toMillis()) {
            while (true) {
                val stopTime = Instant.now()
                val startTime = stopTime.minus(Duration.ofHours(1))
                val queryId = startQuery {
                    it.logGroupName(logGroup)
                    it.startTime(startTime.toEpochMilli())
                    it.endTime(stopTime.toEpochMilli())
                    it.queryString(DEFAULT_INSIGHTS_QUERY_STRING)
                }.queryId()
                log.info("started query with id: $queryId")

                while (true) {
                    val result = getQueryResults {
                        it.queryId(queryId)
                    }

                    if (result.results().size >= 2) {
                        log.info("query returned enough results, continuing")
                        return@withTimeout
                    } else if (result.status() in setOf(QueryStatus.FAILED, QueryStatus.CANCELLED)) {
                        throw IllegalStateException(
                            "Reached terminal condition while waiting for log propagation: queryId: $queryId, status: ${result.statusAsString()}"
                        )
                    } else if (result.status() == QueryStatus.COMPLETE) {
                        log.info("query returned no results, restarting query")
                        break
                    } else {
                        log.info("no results yet, checking again")
                        delay(500)
                    }
                }
                delay(2000)
            }
        }
    }
}
*/
