// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.sqs.toolwindow

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage

@State(name = "sqs", storages = [Storage("aws.xml")])
class MessageCache : PersistentStateComponent<RecentMessage> {
    private var state = RecentMessage()
    override fun getState(): RecentMessage = state
    override fun loadState(state: RecentMessage) {
        this.state = state
    }

    fun getMessage(value: String?): String? {
        val message = state.savedMessage?.get(value)
        return message
    }
    fun setMessage(value: String, key: String) {
        state.savedMessage?.put(value, key)
    }

    companion object {
        @JvmStatic
        fun getInstance(): MessageCache = ServiceManager.getService(MessageCache::class.java)
    }
}

data class RecentMessage(
    var savedMessage: MutableMap<String, String>? = mutableMapOf()
)
