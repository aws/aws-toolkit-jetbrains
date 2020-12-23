// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.uitests.fixtures

import com.intellij.remoterobot.RemoteRobot
import com.intellij.remoterobot.data.RemoteComponent
import com.intellij.remoterobot.fixtures.CommonContainerFixture
import com.intellij.remoterobot.fixtures.ComponentFixture
import com.intellij.remoterobot.fixtures.FixtureName
import com.intellij.remoterobot.search.locators.byXpath
import com.intellij.remoterobot.stepsProcessing.step
import com.intellij.remoterobot.utils.waitFor
import org.assertj.swing.timing.Pause
import java.time.Duration
import kotlin.random.Random

fun IdeaFrame.awsExplorer(
    timeout: Duration = Duration.ofSeconds(20),
    function: AwsExplorer.() -> Unit
) {
    val locator = byXpath("//div[@accessiblename='AWS Explorer Tool Window' and @class='InternalDecorator']")

    step("AWS explorer") {
        val explorer = try {
            find<AwsExplorer>(locator, timeout)
        } catch (e: Exception) {
            step("Open tool window") {
                // Click the tool window stripe
                find(ComponentFixture::class.java, byXpath("//div[@accessiblename='AWS Explorer' and @class='StripeButton' and @text='AWS Explorer']")).click()
                find<AwsExplorer>(locator, timeout)
            }
        }

        explorer.apply(function)
    }
}

@FixtureName("AWSExplorer")
open class AwsExplorer(
    remoteRobot: RemoteRobot,
    remoteComponent: RemoteComponent
) : CommonContainerFixture(remoteRobot, remoteComponent) {
    private fun explorerTree() = find<JTreeFixture>(byXpath("//div[@class='Tree']"))

    fun openExplorerActionMenu(vararg path: String) {
        explorerTree().rightClickPath(*path)
    }

    fun expandExplorerNode(vararg path: String) {
        expandServiceNode(path.first())

        explorerTree().expandPath(*path)
        explorerTree().waitUntilLoaded()
    }

    private fun expandServiceNode(serviceName: String) {
        step("Expand service node '$serviceName'") {
            for (attempt in 1..MAX_ATTEMPTS) {
                val explorerTree = explorerTree()

                waitFor { explorerTree.hasPath(serviceName) }
                explorerTree.expandPath(serviceName)
                explorerTree.waitUntilLoaded()

                step("DEBUG: ${explorerTree.findAllText().joinToString { it.text }}") {}

                if (explorerTree.findAllText().any { remoteText -> remoteText.text.startsWith("Error Loading Resources") }) {
                    if (attempt < MAX_ATTEMPTS) {
                        val pauseTime = Duration.ofSeconds(30 * attempt.toLong() + Random.nextInt(30))
                        step("Error node was returned, will wait and try again in $pauseTime . Attempt $attempt of $MAX_ATTEMPTS") {
                            Pause.pause(pauseTime.toMillis())
                            refresh()
                        }
                    } else {
                        throw IllegalStateException("Max attempts reached")
                    }
                } else {
                    break
                }
            }
        }
    }

    private fun refresh() {
        step("Pressing Refresh...") {
            findAndClick("//div[@accessiblename='Refresh AWS Connection']")
        }
    }

    fun doubleClickExplorer(vararg nodeElements: String) {
        explorerTree().doubleClickPath(*nodeElements)
    }

    private companion object {
        const val MAX_ATTEMPTS = 5
    }
}
