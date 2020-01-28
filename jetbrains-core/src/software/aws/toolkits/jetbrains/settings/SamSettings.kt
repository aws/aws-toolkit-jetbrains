// Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.settings

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import org.jetbrains.annotations.TestOnly
import software.aws.toolkits.jetbrains.core.executables.ExecutableInstance
import software.aws.toolkits.jetbrains.core.executables.ExecutableManager
import software.aws.toolkits.jetbrains.core.executables.getExecutableIfPresent
import software.aws.toolkits.jetbrains.services.lambda.sam.SamExecutable

@State(name = "sam", storages = [Storage("aws.xml")])
class SamSettings : PersistentStateComponent<SamConfiguration> {
    private var state = SamConfiguration()

    override fun getState(): SamConfiguration = state

    override fun loadState(state: SamConfiguration) {
        this.state = state
    }

    /**
     * Returns the path to the SAM CLI executable by first using the manual value,
     * if it is not set attempts to auto-detect it
     */
    val executablePath: String?
        get() = if (state.savedExecutablePath.isNullOrEmpty()) {
            ExecutableManager.getInstance().getExecutableIfPresent<SamExecutable>().let {
                when (it) {
                    is ExecutableInstance.Executable -> it.executablePath.toAbsolutePath().toString()
                    else -> null
                }
            }
        } else {
            state.savedExecutablePath
        }

    /**
     * Exposes the saved (aka manually set) path to SAM CLI executable
     */
    var savedExecutablePath: String?
        get() = state.savedExecutablePath
        set(value) {
            state.savedExecutablePath = value
        }

    companion object {
        @JvmStatic
        @TestOnly
        fun getInstance(): SamSettings = ServiceManager.getService(SamSettings::class.java)
    }
}

data class SamConfiguration(
    var savedExecutablePath: String? = null
)
