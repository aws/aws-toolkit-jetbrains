// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.uitests.fixtures

import com.intellij.remoterobot.RemoteRobot
import com.intellij.remoterobot.data.RemoteComponent
import com.intellij.remoterobot.fixtures.ComponentFixture
import com.intellij.remoterobot.stepsProcessing.step

class JTreeFixture(
    remoteRobot: RemoteRobot,
    remoteComponent: RemoteComponent
) : ComponentFixture(remoteRobot, remoteComponent) {
    fun clickPath(vararg paths: String) = runPath("clickPath", *paths)
    fun expandPath(vararg paths: String) = runPath("expandPath", *paths)
    fun rightClickPath(vararg paths: String) = runPath("rightClickPath", *paths)
    fun doubleClickPath(vararg paths: String) = runPath("doubleClickPath", *paths)

    fun clickRow(row: Int) = runRow("clickRow", row)
    fun expandRow(row: Int) = runRow("expandRow", row)

    private fun runPath(name: String, vararg paths: String) {
        val path = paths.joinToString("/")
        step("$name $path") {
            runJs(
                """
                const jTreeFixture = JTreeFixture(robot, component);
                jTreeFixture.$name('$path') 
                """.trimIndent()
            )
        }
    }

    private fun runRow(name: String, row: Int) {
        step("$name $row") {
            runJs(
                """
                const jTreeFixture = JTreeFixture(robot, component);
                jTreeFixture.$name($row) 
                """.trimIndent()
            )
        }
    }
}
