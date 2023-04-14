// Copyright 2022 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.gateway.connection.workflow.v2

import com.intellij.remote.RemoteCredentials
import com.jetbrains.gateway.ssh.deploy.CommandExecutionResultWithRawStreams
import com.jetbrains.gateway.ssh.deploy.CommandExecutionStreams
import com.jetbrains.gateway.ssh.deploy.HostCommandExecutor
import com.jetbrains.gateway.ssh.deploy.ShellArgument
import com.jetbrains.gateway.ssh.deploy.impl.SshCommandExecutor
import com.jetbrains.rd.util.lifetime.Lifetime

class CawsHostCommandExecutor private constructor(private val delegate: HostCommandExecutor) : HostCommandExecutor by delegate {
    constructor(creds: RemoteCredentials) : this(SshCommandExecutor(creds, allowDialogs = true))

    override suspend fun executeCommand(
        lifetime: Lifetime,
        vararg command: ShellArgument,
        mergeStderrIntoStdout: Boolean,
        useTty: Boolean,
        stringifier: (Array<out ShellArgument>) -> String
    ): CommandExecutionResultWithRawStreams {
        val streams = delegate.executeCommand(lifetime, command = command, mergeStderrIntoStdout, false, stringifier)
        return object : CommandExecutionResultWithRawStreams by streams {
            override fun extractStreams(): CommandExecutionStreams {
                val extracted = streams.extractStreams()
                return extracted
                return extracted.copy(
                    stdout = SsmFilterInputStream(extracted.stdout)
                )
            }
        }
    }
}
