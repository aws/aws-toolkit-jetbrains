// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package migration.software.amazon.q.jetbrains.core.credentials.profiles

import com.intellij.openapi.components.service

interface ProfileWatcher {
    fun addListener(listener: () -> Unit)
    fun forceRefresh() {}

    companion object {
        fun getInstance() = service<ProfileWatcher>()
    }
}
