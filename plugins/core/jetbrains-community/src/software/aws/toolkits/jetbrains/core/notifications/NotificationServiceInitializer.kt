// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.core.notifications

import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import java.util.concurrent.atomic.AtomicBoolean

internal class NotificationServiceInitializer : ProjectActivity {

    private val initialized = AtomicBoolean(false)

    override suspend fun execute(project: Project) {
        if (initialized.compareAndSet(false, true)) {
            val service = NotificationPollingService.getInstance()
            ProcessNotificationsBase(project)
            service.startPolling()
        }
    }
}