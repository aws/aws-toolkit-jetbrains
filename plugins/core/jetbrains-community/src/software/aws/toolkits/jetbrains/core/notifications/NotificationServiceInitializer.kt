// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.core.notifications

import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import java.util.concurrent.atomic.AtomicBoolean

internal class NotificationServiceInitializer : ProjectActivity {
    companion object {
        private val initialized = AtomicBoolean(false)
    }

    override suspend fun execute(project: Project) {
        val service = NotificationPollingService.getInstance()
        ProcessNotificationsBase()
        if (initialized.compareAndSet(false, true)) {
            service.startPolling()
        }
    }
}
