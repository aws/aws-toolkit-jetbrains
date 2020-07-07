// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.uitests.fixtures

import com.intellij.remoterobot.RemoteRobot
import com.intellij.remoterobot.data.RemoteComponent
import com.intellij.remoterobot.fixtures.FixtureName
import com.intellij.remoterobot.search.locators.byXpath
import com.intellij.remoterobot.stepsProcessing.step
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
    fun openExplorerActionMenu(vararg path: String) {
        findExplorerTree().rightClickPath(*path)
    }

    fun expandExplorerNode(vararg path: String) {
        findExplorerTree().expandPath(*path)
        // TODO clean this up, base on when it loads the child nodes (think of a method to do it)
        Thread.sleep(5000)
    }

    fun doubleClickExplorer(vararg nodeElements: String) {
        findExplorerTree().doubleClickPath(*nodeElements)
    }

    private fun findExplorerTree() = find<JTreeFixture>(byXpath("//div[@class='Tree']"), Duration.ofSeconds(10))
}
