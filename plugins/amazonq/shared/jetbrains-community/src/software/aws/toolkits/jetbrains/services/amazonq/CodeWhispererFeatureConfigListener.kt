// Copyright 2023 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonq

import com.intellij.openapi.application.ApplicationManager
import com.intellij.util.messages.Topic

interface CodeWhispererFeatureConfigListener {
    fun publishFeatureConfigsAvailble() {}

    companion object {
        @Topic.AppLevel
        val TOPIC = Topic.create("feature configs listener", CodeWhispererFeatureConfigListener::class.java)

        fun notifyUiFeatureConfigsAvailable() {
            ApplicationManager.getApplication().messageBus.syncPublisher(TOPIC).publishFeatureConfigsAvailble()
        }
    }
}
