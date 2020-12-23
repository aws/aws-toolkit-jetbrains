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
import org.assertj.swing.timing.Pause
import java.time.Duration

fun IdeaFrame.awsExplorer(
    timeout: Duration = Duration.ofSeconds(20),
    function: AwsExplorer.() -> Unit
) {
    val locator = byXpath("//div[@accessiblename='AWS Explorer Tool Window' and @class='InternalDecorator']")

    step("AWS explorer") {
        val explorer = try {
            find<AwsExplorer>(locator, timeout)
        } catch (e: Exception) {
            // Click the tool window stripe
            find(ComponentFixture::class.java, byXpath("//div[@accessiblename='AWS Explorer' and @class='StripeButton' and @text='AWS Explorer']")).click()
            find<AwsExplorer>(locator, timeout)
        }

        explorer.apply(function)
    }
}

@FixtureName("AWSExplorer")
open class AwsExplorer(
    remoteRobot: RemoteRobot,
    remoteComponent: RemoteComponent
) : CommonContainerFixture(remoteRobot, remoteComponent) {
    private val explorerTree by lazy {
        find<JTreeFixture>(byXpath("//div[@class='Tree']"))
    }

    fun openExplorerActionMenu(vararg path: String) {
        explorerTree.rightClickPath(*path)
    }

    fun expandExplorerNode(vararg path: String) {
        expandServiceNode(path.first())

        explorerTree.expandPath(*path)
        explorerTree.waitUntilLoaded()
    }

    private fun expandServiceNode(serviceName: String) {
        repeat(MAX_ATTEMPTS) {
            val attempt = it + 1
            explorerTree.expandPath(serviceName)
            explorerTree.waitUntilLoaded()

            if (explorerTree.hasText { remoteText -> remoteText.text.startsWith("Error Loading Resources") }) {
                val pauseTime = Duration.ofSeconds(2 * attempt.toLong())
                step("Error node was returned, will wait and try again in $pauseTime . Attempt $attempt of $MAX_ATTEMPTS") {
                    Pause.pause(pauseTime.toMillis())
                    refresh()
                }
            } else {
                return
            }
        }
    }

    private fun refresh() {
        step("Pressing Refresh...") {
            findAndClick("//div[@accessiblename='Refresh AWS Connection']")
        }
    }

    fun doubleClickExplorer(vararg nodeElements: String) {
        explorerTree.doubleClickPath(*nodeElements)
    }

    private companion object {
        const val MAX_ATTEMPTS = 5
    }
}
