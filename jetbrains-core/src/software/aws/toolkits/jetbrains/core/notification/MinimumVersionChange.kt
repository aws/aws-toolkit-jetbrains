// Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.core.notification

import com.intellij.ide.util.PropertiesComponent
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationDisplayType
import com.intellij.notification.NotificationGroup
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.extensions.ExtensionNotApplicableException
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity
import software.aws.toolkits.resources.message

class MinimumVersionChange @JvmOverloads constructor(isUnderTest: Boolean = false) : StartupActivity.DumbAware {
    private val minVersionHuman = "2020.3"
    private val minVersion = 203

    init {
        if (ApplicationManager.getApplication().isUnitTestMode && !isUnderTest) {
            throw ExtensionNotApplicableException.INSTANCE
        }
    }

    override fun runActivity(project: Project) {
        if (System.getProperty(SKIP_PROMPT)?.toBoolean() == true) {
            return
        }

        // Setting is stored application wide
        if (PropertiesComponent.getInstance().getBoolean(IGNORE_PROMPT)) {
            return
        }

        if (ApplicationInfo.getInstance().build.baselineVersion > minVersion) {
            return
        }

        val title = message("aws.toolkit_deprecation.title")
        val message = message(
            "aws.toolkit_deprecation.message",
            ApplicationNamesInfo.getInstance().fullProductName,
            ApplicationInfo.getInstance().fullVersion,
            minVersionHuman
        )

        // TODO: Migrate to this FIX_WHEN_MIN_IS_203
//        val notificationGroup = NotificationGroupManager.getInstance().getNotificationGroup("aws.toolkit_deprecation")
        NOTIFICATION_GROUP.createNotification(title, message, NotificationType.WARNING)
            .addAction(
                NotificationAction.createSimpleExpiring(message("general.notification.action.hide_forever")) {
                    PropertiesComponent.getInstance().setValue(IGNORE_PROMPT, true)
                }
            )
            .notify(project)
    }

    companion object {
        // Used by tests to make sure the prompt never shows up
        const val SKIP_PROMPT = "aws.suppress_deprecation_prompt"
        const val IGNORE_PROMPT = "aws.ignore_deprecation_prompt"

        private val NOTIFICATION_GROUP = NotificationGroup(
            "aws.toolkit_deprecation",
            NotificationDisplayType.STICKY_BALLOON,
            true,
            null,
            null,
            message("aws.toolkit_deprecation.title"),
        )
    }
}
