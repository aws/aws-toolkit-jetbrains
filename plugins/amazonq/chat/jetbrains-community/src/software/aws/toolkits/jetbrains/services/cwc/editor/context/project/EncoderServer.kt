// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cwc.editor.context.project

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.ProcessOutput
import com.intellij.execution.util.ExecUtil
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import software.aws.toolkits.core.utils.getLogger
import java.util.concurrent.atomic.AtomicBoolean

@Service(Service.Level.PROJECT)
class EncoderServer (val project: Project): Disposable {
    private val isRunning = AtomicBoolean(false)
    private val portManager = EncoderServerPortManager.getInstance()
    private lateinit var serverThread: Thread
    private var processOutput: ProcessOutput? = null
    private var numberOfRetry = 0
    var currentPort: String = portManager.getPort()

    private val serverRunnable = Runnable {
        logger.info("encoder port : $currentPort")
        while (numberOfRetry < 3) {
            runCommand(getCommand())
        }
    }

    private fun runCommand (command: GeneralCommandLine) {
        try {
            processOutput = ExecUtil.execAndGetOutput(command)
            logger.info("encoder server output: ${processOutput?.stdout}")
            if(processOutput?.exitCode != 0) {
                throw Exception(processOutput?.stderr)
            }
        } catch (e: Exception){
            logger.info("error running encoder server: $e")
            if(e.stackTraceToString().contains("address already in use")) {
                portManager.addUsedPort(currentPort)
                numberOfRetry++
                currentPort = portManager.getPort()
            } else {
                throw Exception(e.message)
            }
        }
    }

    private fun getCommand (): GeneralCommandLine {
        val map = mutableMapOf<String, String>()
        map["PORT"] = currentPort
        val command = GeneralCommandLine("/Users/zoelin/Workspace/encoder/dist/node", "/Users/zoelin/Workspace/encoder/dist/extension.js")
            .withParentEnvironmentType(GeneralCommandLine.ParentEnvironmentType.CONSOLE)
            .withEnvironment(map)
        return command
    }

    fun isServerRunning(): Boolean = isRunning.get()

    fun start() {
        if (!isRunning.getAndSet(true)) {
            serverThread = Thread(serverRunnable)
            serverThread.start()
        } else {
            throw IllegalStateException("Encoder server is already running!")
        }
    }

    fun close() {
        if (isRunning.getAndSet(false)) {
            serverThread.interrupt()
        }
    }

    override fun dispose() {
        close()
    }

    companion object {
        private val logger = getLogger<EncoderServer>()

        fun getInstance(project: Project) = project.service<EncoderServer>()
    }
}
