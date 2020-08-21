// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.settings

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.project.Project

@State(name = "updateLambdaSettings", storages = [Storage("aws.xml")])
class UpdateLambdaSettings : PersistentStateComponent<UpdateSettings> {
    private var state = UpdateSettings()

    override fun getState(): UpdateSettings? = state
    override fun loadState(state: UpdateSettings) {
        this.state = state
    }

    fun useContainer(stackArn: String): Boolean? = state.samConfigs[stackArn]?.useContainer
    fun setUseContainer(stackArn: String, value: Boolean) {
        state.samConfigs.computeIfAbsent(stackArn) { UpdateConfig() }.useContainer = value
    }

    fun bucketName(stackArn: String): String? = state.samConfigs[stackArn]?.bucketName
    fun setBucketName(stackArn: String, value: String?) {
        state.samConfigs.computeIfAbsent(stackArn) { UpdateConfig() }.bucketName = value
    }

    companion object {
        @JvmStatic
        fun getInstance(project: Project): UpdateLambdaSettings = ServiceManager.getService(project, UpdateLambdaSettings::class.java)
    }
}

data class UpdateSettings(
    var samConfigs: MutableMap<String, UpdateConfig> = mutableMapOf()
)

data class UpdateConfig(
    var bucketName: String? = null,
    var useContainer: Boolean = false
)
