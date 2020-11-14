// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.core.terminal

import com.intellij.openapi.project.Project
import org.jetbrains.plugins.terminal.LocalTerminalDirectRunner
import software.amazon.awssdk.auth.credentials.AwsCredentials
import software.aws.toolkits.core.region.AwsRegion
import software.aws.toolkits.core.region.toEnvironmentVariables
import software.aws.toolkits.core.utils.getLogger
import software.aws.toolkits.jetbrains.core.credentials.toEnvironmentVariables

@Suppress("UnstableApiUsage") // TODO Remove after 2020.3 FIX_WHEN_MIN_IS_203
class AwsLocalTerminalRunner(project: Project, private val region: AwsRegion, private val credentials: AwsCredentials) : LocalTerminalDirectRunner(project) {
    override fun getCommands(envs: MutableMap<String, String>): MutableList<String> {
        region.toEnvironmentVariables(envs, replace = true)
        credentials.toEnvironmentVariables(envs, replace = true)
        return super.getCommands(envs)
    }

    companion object {
        val LOG = getLogger<AwsLocalTerminalRunner>()
    }
}
