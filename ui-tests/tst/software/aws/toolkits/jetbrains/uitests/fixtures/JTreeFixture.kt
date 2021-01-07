// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.uitests.fixtures

import com.intellij.remoterobot.RemoteRobot
import com.intellij.remoterobot.data.RemoteComponent
import com.intellij.remoterobot.fixtures.ComponentFixture
import com.intellij.remoterobot.stepsProcessing.step
import com.intellij.remoterobot.utils.waitFor
import org.assertj.swing.timing.Pause
import java.time.Duration

/**
 * JTreeFixture designed to work with models that use AsyncTreeModel
 */
class JTreeFixture(
    remoteRobot: RemoteRobot,
    remoteComponent: RemoteComponent
) : ComponentFixture(remoteRobot, remoteComponent) {
    var separator: String = "/"

    fun hasPath(vararg path: String): Boolean {
        val fullPath = path.joinToString(separator)
        return step("hasPath $fullPath") {
           callJs(
                """
                const jTreeFixture = JTreeFixture(robot, component);
                jTreeFixture.replaceSeparator('$separator')
                try {
                    // Throws LocationUnavailableException if not found
                    jTreeFixture.node('$fullPath') 
                    true
                } catch(e) {
                    false
                }
                """.trimIndent()
            )
        }
    }

    fun expandPath(vararg path: String) = expandAndWait(*path)

    fun clickPath(vararg path: String) {
        expandAndWait(*path.dropLast(1).toTypedArray())
        runJsPathMethod("clickPath", *path)
    }

    fun rightClickPath(vararg path: String) {
        expandAndWait(*path.dropLast(1).toTypedArray())
        runJsPathMethod("rightClickPath", *path)
    }

    fun doubleClickPath(vararg path: String) {
        expandAndWait(*path.dropLast(1).toTypedArray())
        runJsPathMethod("doubleClickPath", *path)
    }

    private fun expandAndWait(vararg path: String) {
        // Expand each path segment and wait for it to load before loading the next segment
        for(i in 1..path.size) {
            val subPath = path.copyOfRange(0, i)
            runJsPathMethod("expandPath", *subPath)
            waitUntilLoaded(*subPath)
        }
    }

    fun waitUntilLoaded(vararg path: String) {
        step("waiting for loading text to go away...") {
            Pause.pause(100)
            waitFor(duration = Duration.ofMinutes(1)) {
                !this.hasPath(*path, "loading...")
            }
            Pause.pause(100)
        }
    }

    fun requireSelection(vararg path: String) {
        val fullPath = path.joinToString(separator)
        step("requireSelection $fullPath") {
            runJs(
                """
                const jTreeFixture = JTreeFixture(robot, component);
                jTreeFixture.replaceSeparator('$separator')
                // Have to disambiguate int[] vs string[]
                jTreeFixture['requireSelection(java.lang.String[])'](['$fullPath']) 
                """.trimIndent()
            )
        }
    }

    private fun runJsPathMethod(name: String, vararg path: String) {
        val fullPath = path.joinToString(separator)
        step("$name $fullPath") {
            runJs(
                """
                const jTreeFixture = JTreeFixture(robot, component);
                jTreeFixture.replaceSeparator('$separator')
                jTreeFixture.$name('$fullPath') 
                """.trimIndent()
            )
        }
    }
}
