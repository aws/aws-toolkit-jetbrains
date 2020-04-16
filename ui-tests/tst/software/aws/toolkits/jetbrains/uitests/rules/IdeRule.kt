// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.uitests.rules

import com.intellij.remoterobot.RemoteRobot
import com.intellij.remoterobot.search.locators.LambdaLocator
import com.intellij.remoterobot.stepsProcessing.StepLogger
import com.intellij.remoterobot.stepsProcessing.StepWorker
import com.intellij.remoterobot.stepsProcessing.step
import com.intellij.remoterobot.utils.waitFor
import org.gradle.tooling.CancellationTokenSource
import org.gradle.tooling.GradleConnectionException
import org.gradle.tooling.GradleConnector
import org.gradle.tooling.ProjectConnection
import org.gradle.tooling.ResultHandler
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import software.aws.toolkits.jetbrains.uitests.fixtures.DialogFixture
import software.aws.toolkits.jetbrains.uitests.fixtures.WelcomeFrame
import java.awt.Window
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.file.Files
import java.nio.file.Paths
import java.time.Duration
import java.util.concurrent.atomic.AtomicBoolean

private val initialSetup = AtomicBoolean(false)
private val robotPort = System.getProperty("robot-server.port")?.toInt() ?: throw IllegalStateException("System Property 'robot-server.port' is not set")

fun uiTest(test: RemoteRobot.() -> Unit) {
    if (!initialSetup.getAndSet(true)) {
        StepWorker.registerProcessor(StepLogger())
    }

    RemoteRobot("http://127.0.0.1:$robotPort").apply(test)
}

class Ide(private val gradleTask: String) : TestWatcher() {
    private val gradleProcess = GradleProcess

    override fun starting(description: Description) {
        if (!gradleProcess.isCorrectGradleTask(gradleTask)) {
            gradleProcess.stopGradleTask()
        }

        gradleProcess.startGradleTask(gradleTask)
        waitForIde()
    }

    override fun finished(description: Description) {
        uiTest {
            step("Attempt to reset to Welcome Frame") {
                // Try to get back to starting point by closing all windows
                val dialogs = findAll<DialogFixture>(LambdaLocator("all dialogs") {
                    it is Window && it.isShowing
                })

                dialogs.filterNot { it.remoteComponent.className.contains("FlatWelcomeFrame") }
                    .forEach {
                        step("Closing ${it.title}") { it.close() }
                    }

                // Make sure we find the welcome screen
                find<WelcomeFrame>()
            }
        }
    }

    private fun waitForIde() {
        waitFor(
            duration = Duration.ofMinutes(10),
            interval = Duration.ofMillis(500),
            errorMessage = "Could not connect to remote robot in time"
        ) {
            if (!gradleProcess.isRunning()) {
                throw IllegalStateException("Gradle task has ended, check log")
            }

            canConnectToToRobot()
        }
    }

    private fun canConnectToToRobot(): Boolean = try {
        Socket().use { socket ->
            socket.connect(InetSocketAddress("127.0.0.1", robotPort))
            true
        }
    } catch (e: IOException) {
        false
    }
}

private object GradleProcess {
    private data class Execution(val task: String, val cancellationTokenSource: CancellationTokenSource)

    private val gradleConnection: ProjectConnection
    private var lastGradleTask: Execution? = null
    private val isRunning = AtomicBoolean(false)

    init {
        val cwd = Paths.get(".").toAbsolutePath()
        if (!Files.exists(cwd.resolve("build.gradle"))) {
            throw IllegalStateException("Failed to locate build.gradle in $cwd}")
        }

        gradleConnection = GradleConnector.newConnector()
            .forProjectDirectory(cwd.toFile())
            .connect()

        println("Connected to Gradle")
    }

    fun isRunning(): Boolean = isRunning.get()

    fun isCorrectGradleTask(gradleTask: String): Boolean = lastGradleTask?.task == gradleTask && isRunning()

    fun startGradleTask(gradleTask: String) {
        val tokenSource = GradleConnector.newCancellationTokenSource()

        gradleConnection.newBuild().forTasks(gradleTask)
            .withCancellationToken(tokenSource.token())
            .setColorOutput(false)
            .setStandardOutput(System.out)
            .run(object : ResultHandler<Any> {
                override fun onFailure(failure: GradleConnectionException) {
                    isRunning.set(false)
                }

                override fun onComplete(result: Any) {
                    isRunning.set(false)
                }
            })

        isRunning.set(true)

        lastGradleTask = Execution(gradleTask, tokenSource)
    }

    fun stopGradleTask() {
        lastGradleTask?.let {
            println("Stopping Gradle task ${it.task}")
            it.cancellationTokenSource.cancel()

            lastGradleTask = null
        }
    }
}

