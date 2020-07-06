// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.uitests.fixtures

import com.intellij.remoterobot.RemoteRobot
import com.intellij.remoterobot.data.RemoteComponent
import com.intellij.remoterobot.fixtures.FixtureName
import com.intellij.remoterobot.search.locators.byXpath
import com.intellij.remoterobot.stepsProcessing.step
import com.intellij.remoterobot.utils.keyboard
import java.awt.event.KeyEvent
import java.time.Duration

fun RemoteRobot.awsExplorer(
    timeout: Duration = Duration.ofSeconds(20),
    function: AwsExplorer.() -> Unit
) {
    step("AWS explorer") {
        find<AwsExplorer>(byXpath("//div[@class='ExplorerToolWindow']"), timeout).apply(function)
    }
}

@FixtureName("AWSExplorer")
open class AwsExplorer(
    remoteRobot: RemoteRobot,
    remoteComponent: RemoteComponent
) : DialogFixture(remoteRobot, remoteComponent) {

    fun openExplorerActionMenu(nodeName: String) {
        findExplorerTree().rightClickPath(nodeName)
    }

    fun expandExplorerNode(nodeName: String) {
        findExplorerTree().clickPath(nodeName)
        // We can't find the carrot to expand, so use enter to expand
        keyboard { key(KeyEvent.VK_ENTER) }
        // wait for the node to load
        // TODO clean this up
        Thread.sleep(5000)
    }

    fun doubleClickExplorer(nodeName: String) {
        findExplorerTree().doubleClickPath(nodeName)
    }

    private fun findExplorerTree() = find<JTreeFixture>(byXpath("//div[@class='Tree']"), Duration.ofSeconds(10))
}
