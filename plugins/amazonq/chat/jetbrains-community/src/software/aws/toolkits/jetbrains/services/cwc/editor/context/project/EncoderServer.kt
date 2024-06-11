// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cwc.editor.context.project

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.util.ExecUtil
import software.aws.toolkits.core.utils.getLogger
import java.util.concurrent.atomic.AtomicBoolean

class EncoderServer {
    private val isRunning = AtomicBoolean(false)
    private lateinit var serverThread: Thread

    private val serverRunnable = Runnable {
        val command = GeneralCommandLine("node", "/Users/zoelin/Workspace/encoder/dist/extension.js")
        val processOutput = ExecUtil.execAndGetOutput(command)
        logger.info("encoder server output: $processOutput")
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

    companion object {
        private val logger = getLogger<EncoderServer>()
        private val instance = EncoderServer()

        fun getInstance() = instance
    }
}
