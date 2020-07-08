// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.uitests.fixtures

import com.intellij.remoterobot.RemoteRobot
import com.intellij.remoterobot.data.RemoteComponent
import com.intellij.remoterobot.fixtures.CommonContainerFixture
import com.intellij.remoterobot.fixtures.ContainerFixture
import com.intellij.remoterobot.fixtures.FixtureName
import com.intellij.remoterobot.search.locators.byXpath
import com.intellij.remoterobot.stepsProcessing.log
import com.intellij.remoterobot.stepsProcessing.step

fun ContainerFixture.editorTab(title: String, function: EditorTab.() -> Unit = {}): EditorTab {
    val editorTabb = find<EditorTab>(byXpath("//div[@class='EditorTabs']//div[@accessiblename='$title' and @class='SingleHeightLabel']"))
    editorTabb.click()
    // On Linux this also opens a "save context menu", so close that if it is open
    step("Close save context menu (if it opens)") {
        try {
            pressCancel()
            log.info("Closed the menu")
        } catch (e: Exception) {
            log.info("No save context menu opened")
        }
    }

    return editorTabb.apply(function)
}

@FixtureName("EditorTab")
class EditorTab(remoteRobot: RemoteRobot, remoteComponent: RemoteComponent) : CommonContainerFixture(remoteRobot, remoteComponent)
