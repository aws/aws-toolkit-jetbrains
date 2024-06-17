// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cwc.editor.context.project

import java.util.concurrent.atomic.AtomicInteger

class EncoderServerPortManager {
    private val usedPorts = mutableSetOf<Int>()
    private val startingPort = 3701
    private var currentPort: AtomicInteger = AtomicInteger(startingPort)

    fun addUsedPort(port: String) {
        usedPorts.add(port.toInt())
    }

    fun getPort(): String {
        while (usedPorts.contains(currentPort.get())) {
            currentPort.incrementAndGet()
        }
        usedPorts.add(currentPort.get())
        return currentPort.get().toString()
    }

    companion object {
        private val instance = EncoderServerPortManager()
        fun getInstance() = instance
    }
}
