// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.settings

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage

@State(name = "updateLambdaSettings", storages = [Storage("aws.xml")])
class UpdateLambdaSettings : PersistentStateComponent<UpdateSettings> {
    private var state = UpdateSettings()

    override fun getState(): UpdateSettings? = state
    override fun loadState(state: UpdateSettings) {
        this.state = state
    }

    fun useContainer(stackArn: String): Boolean? = state.updateConfigs[stackArn]?.useContainer
    fun setUseContainer(stackArn: String, value: Boolean) {
        state.updateConfigs.computeIfAbsent(stackArn) { UpdateConfig() }.useContainer = value
    }

    fun bucketName(stackArn: String): String? = state.updateConfigs[stackArn]?.bucketName
    fun setBucketName(stackArn: String, value: String?) {
        state.updateConfigs.computeIfAbsent(stackArn) { UpdateConfig() }.bucketName = value
    }

    companion object {
        @JvmStatic
        fun getInstance(): UpdateLambdaSettings = ServiceManager.getService(UpdateLambdaSettings::class.java)
    }
}

data class UpdateSettings(
    var updateConfigs: MutableMap<String, UpdateConfig> = mutableMapOf()
)

data class UpdateConfig(
    var bucketName: String? = null,
    var useContainer: Boolean = false
)
