// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.ui.s3

import com.intellij.testGuiFramework.impl.button
import com.intellij.testGuiFramework.impl.jTree
import com.intellij.testGuiFramework.impl.textfield
import com.intellij.testGuiFramework.impl.waitAMoment
import com.intellij.testGuiFramework.util.step
import org.junit.Test
import software.aws.toolkits.jetbrains.EmptyProjectTestCase
import software.aws.toolkits.jetbrains.fixtures.clickMenuItem
import software.aws.toolkits.jetbrains.fixtures.configureConnection
import java.util.UUID

class S3BrowserTest : EmptyProjectTestCase() {

    private val profile = "Profile:default"
    private val bucket = "uitest-${UUID.randomUUID()}"

    @Test
    fun s3MainFunctionality() {

        ideFrame {
            waitForBackgroundTasksToFinish()

            configureConnection(profile, "Oregon (us-west-2)")

            toolwindow("aws.explorer") {
                activate()
                step("Create bucket named $bucket") {
                    jTree("S3").path("S3").rightClickPath()
                    clickMenuItem { it.text.contains("Create") }
                    waitAMoment()
                    textfield("Bucket Name:").setText(bucket)
                    button("Create").click()
                    waitAMoment()
                }

                step("Delete bucket named $bucket") {
                    with(jTree("S3").expandPath("S3")) {
                        path("S3", bucket).rightClickPath()
                    }

                    clickMenuItem { it.text.contains("Delete") }
                    waitAMoment()
                    typeText(bucket)
                    button("OK").clickWhenEnabled()
                }
            }
        }
    }
}
