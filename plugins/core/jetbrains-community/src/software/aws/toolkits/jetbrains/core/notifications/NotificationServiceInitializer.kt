// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.core.notifications

import com.intellij.ide.util.RunOnceUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity

class NotificationServiceInitializer : ProjectActivity {

    override suspend fun execute(project: Project) {
        val service = NotificationPollingServiceImpl.getInstance()
        RunOnceUtil.runOnceForApp(this::class.qualifiedName.toString()) {
            service.startPolling()
        }
    }
}
