// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.core

import com.intellij.execution.configurations.GeneralCommandLine
import software.aws.toolkit.core.ClientConnectionSettings
import software.aws.toolkit.core.ConnectionSettings
import software.aws.toolkit.core.credentials.toEnvironmentVariables
import software.aws.toolkit.jetbrains.core.Resource
import software.aws.toolkits.jetbrains.core.executables.ExecutableInstance
import software.aws.toolkits.jetbrains.core.executables.ExecutableManager
import software.aws.toolkits.jetbrains.core.executables.ExecutableType
import java.time.Duration
import kotlin.reflect.KClass

class ExecutableBackedCacheResource<ReturnType, ExecType : ExecutableType<*>>(
    private val executableTypeClass: KClass<ExecType>,
    override val id: String,
    private val expiry: Duration? = null,
    private val fetchCall: GeneralCommandLine.() -> ReturnType,
) : Resource.Cached<ReturnType>() {

    override fun fetch(connectionSettings: ClientConnectionSettings<*>): ReturnType {
        val executableType = ExecutableType.getExecutable(executableTypeClass.java)

        val executable = ExecutableManager.getInstance().getExecutableIfPresent(executableType).let {
            when (it) {
                is ExecutableInstance.Executable -> it
                is ExecutableInstance.InvalidExecutable, is ExecutableInstance.UnresolvedExecutable ->
                    throw IllegalStateException((it as ExecutableInstance.BadExecutable).validationError)
            }
        }

        return fetchCall(
            executable.getCommandLine()
                .withEnvironment(connectionSettings.region.toEnvironmentVariables())
                .apply {
                    if (connectionSettings is ConnectionSettings) {
                        withEnvironment(connectionSettings.credentials.resolveCredentials().toEnvironmentVariables())
                    }
                }
        )
    }

    override fun expiry(): Duration = expiry ?: super.expiry()
    override fun toString(): String = "ExecutableBackedCacheResource(id='$id')"
}
