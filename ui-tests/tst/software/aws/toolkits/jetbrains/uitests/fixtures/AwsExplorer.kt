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
    step("Search for Project Structure dialog") {
        val dialog = find<AwsExplorer>(byXpath("//div[@class='ExplorerToolWindow']"), timeout)

        dialog.apply(function)

        if (dialog.isShowing) {
            dialog.close()
        }
    }
}

@FixtureName("AWSExplorer")
open class AwsExplorer(
    remoteRobot: RemoteRobot,
    remoteComponent: RemoteComponent
) : DialogFixture(remoteRobot, remoteComponent)
