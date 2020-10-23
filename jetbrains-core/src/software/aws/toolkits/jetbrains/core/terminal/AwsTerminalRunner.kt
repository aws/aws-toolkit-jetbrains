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

@Suppress("UnstableApiUsage") // TODO move to [LocalTerminalDirectRunner.getInitialCommand] after 2020.3 FIX_WHEN_MIN_IS_203
class AwsTerminalRunner(project: Project, private val region: AwsRegion, private val credentials: AwsCredentials) : LocalTerminalDirectRunner(project) {
    override fun getCommand(envs: MutableMap<String, String>): Array<String> {
        region.toEnvironmentVariables(envs, replace = true)
        credentials.toEnvironmentVariables(envs, replace = true)
        return super.getCommand(envs)
    }

    companion object {
        val LOG = getLogger<AwsTerminalRunner>()
    }
}
