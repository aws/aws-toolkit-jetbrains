// Copyright 2022 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkit.jetbrains.core.credentials.sso.bearer

import com.intellij.openapi.application.ApplicationManager
import com.intellij.util.messages.Topic
import java.util.EventListener

interface BearerTokenProviderListener : EventListener {
    /**
     * Called when token permissions have potentially changed, or is no longer logged in
     */
    fun onProviderChange(providerId: String, newScopes: List<String>? = null) {}

    /**
     * Called when token has changed but connection properties are the same
     */
    fun onTokenModified(providerId: String) {}

    /**
     * Called when provider is being deleted
     */
    fun invalidate(providerId: String) {}

    companion object {
        @Topic.AppLevel
        val TOPIC = Topic.create("AWS SSO bearer token provider status change", BearerTokenProviderListener::class.java)

        fun notifyCredUpdate(providerId: String) {
            ApplicationManager.getApplication().messageBus.syncPublisher(TOPIC).onProviderChange(providerId)
        }
    }
}
