// Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.ui.wizard

import com.intellij.testGuiFramework.impl.GuiTestCase
import com.intellij.testGuiFramework.impl.button
import com.intellij.testGuiFramework.impl.jList
import com.intellij.testGuiFramework.impl.waitAMoment
import org.fest.swing.timing.Pause
import org.junit.Test

class SamInitProjectBuilderIntelliJTest : GuiTestCase() {
    @Test
    fun testNewFromTemplate_defaults() {
        welcomeFrame {
            createNewProject()
            // defensive wait...
            Pause.pause(500)
            dialog("New Project") {
                jList("AWS").clickItem("AWS")
                jList("AWS Serverless Application").clickItem("AWS Serverless Application")
                button("Next").click()
                button("Finish").click()
            }
            // wait for background tasks
            waitAMoment()
        }
    }
}