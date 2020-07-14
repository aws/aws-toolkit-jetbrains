// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.uitests.fixtures

import com.intellij.remoterobot.RemoteRobot
import com.intellij.remoterobot.data.RemoteComponent
import com.intellij.remoterobot.fixtures.CommonContainerFixture
import com.intellij.remoterobot.fixtures.ContainerFixture
import com.intellij.remoterobot.fixtures.FixtureName
import com.intellij.remoterobot.search.locators.byXpath

fun ContainerFixture.editorTab(title: String, function: EditorTab.() -> Unit = {}): EditorTab {
    val editorTabb = find<EditorTab>(
        byXpath(
            // FIX_WHEN_MIN_IS_202 remove the SingleHeightLabel one
            "//div[@accessiblename='$title' and @class='SingleHeightLabel']|//div[@accessiblename='$title' and @class='SimpleColoredComponent']"
        )
    )
    editorTabb.click()
    return editorTabb.apply(function)
}

@FixtureName("EditorTab")
class EditorTab(remoteRobot: RemoteRobot, remoteComponent: RemoteComponent) : CommonContainerFixture(remoteRobot, remoteComponent)
