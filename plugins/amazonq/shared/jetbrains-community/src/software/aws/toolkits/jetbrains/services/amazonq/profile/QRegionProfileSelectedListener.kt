// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonq.profile

import com.intellij.util.messages.Topic

interface QRegionProfileSelectedListener {
    companion object {
        @Topic.AppLevel
        val TOPIC = Topic.create("QRegionProfileSelected", QRegionProfileSelectedListener::class.java)
    }

    fun onProfileSelected(profile: QRegionProfile?)
}
