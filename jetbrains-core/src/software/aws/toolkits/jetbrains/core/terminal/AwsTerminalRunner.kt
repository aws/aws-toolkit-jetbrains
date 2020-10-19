// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.core.terminal

import com.intellij.openapi.project.Project
import org.jetbrains.plugins.terminal.LocalTerminalDirectRunner
import software.aws.toolkits.jetbrains.core.credentials.ConnectionSettings
import software.aws.toolkits.jetbrains.core.credentials.safelyApplyTo

@Suppress("UnstableApiUsage") // TODO move to [LocalTerminalDirectRunner.getInitialCommand] after 2020.3 FIX_WHEN_MIN_IS_203
class AwsTerminalRunner(project: Project, private val connection: ConnectionSettings) : LocalTerminalDirectRunner(project) {
    override fun getCommand(envs: MutableMap<String, String>): Array<String> {
        connection.safelyApplyTo(envs)
        return super.getCommand(envs)
    }
}
