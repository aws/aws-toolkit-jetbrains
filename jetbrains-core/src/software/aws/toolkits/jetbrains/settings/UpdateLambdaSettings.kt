// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.settings

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage

@State(name = "updateLambdaState", storages = [Storage("aws.xml")])
private class UpdateLambdaState : PersistentStateComponent<UpdateLambda> {
    private var settings = UpdateLambda()

    override fun getState(): UpdateLambda = settings
    override fun loadState(state: UpdateLambda) {
        this.settings = state
    }

    companion object {
        @JvmStatic
        internal fun getInstance(): UpdateLambdaState = ServiceManager.getService(UpdateLambdaState::class.java)
    }
}

class UpdateLambdaSettings private constructor(private val arn: String) {
    private val stateService = UpdateLambdaState.getInstance()

    var useContainer: Boolean?
        get() = stateService.state.configs[arn]?.useContainer
        set(value) {
            stateService.state.configs.computeIfAbsent(arn) { UpdateConfig() }.useContainer = value ?: false
        }

    var bucketName: String?
        get() = stateService.state.configs[arn]?.bucketName
        set(value) {
            stateService.state.configs.computeIfAbsent(arn) { UpdateConfig() }.bucketName = value
        }

    companion object {
        fun getInstance(arn: String) = UpdateLambdaSettings(arn)
    }
}

data class UpdateLambda(
    var configs: MutableMap<String, UpdateConfig> = mutableMapOf()
)

data class UpdateConfig(
    var bucketName: String? = null,
    var useContainer: Boolean = false
)
