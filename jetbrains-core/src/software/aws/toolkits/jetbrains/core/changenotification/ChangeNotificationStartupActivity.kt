// Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.core.changenotification

import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity

class ChangeNotificationStartupActivity : StartupActivity {
    override fun runActivity(project: Project) {
        val changeNotificationManager =
            ServiceManager.getService(ChangeNotificationManager::class.java)

        val notices = changeNotificationManager.getRequiredNotices(ChangeType.changes(), project)
        changeNotificationManager.notify(notices, project)
    }
}
