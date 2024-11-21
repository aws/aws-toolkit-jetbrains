// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.core.notifications

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.util.registry.Registry

class DummyNotificationAction : AnAction("Show notif") {
    override fun actionPerformed(e: AnActionEvent) {
        // TODO: Delete before release
        if (!Registry.`is`("aws.toolkit.developerMode")) return

        val notificationWithValidConnectionData = NotificationData(
            id = "example_id_12344",
            schedule = NotificationSchedule(type = "StartUp"),
            severity = "Critical",
            condition = NotificationDisplayCondition(
                compute = null,
                os = null,
                ide = null,
                extension = null,
                authx = listOf(
                    AuthxType(
                        feature = "q",
                        type = NotificationExpression.AnyOfCondition(listOf("Idc", "BuilderId")),
                        region = NotificationExpression.ComparisonCondition("us-east-1"),
                        connectionState = NotificationExpression.ComparisonCondition("Connected"),
                        ssoScopes = null
                    )
                )
            ),
            actions = emptyList(),
            content = NotificationContentDescriptionLocale(
                NotificationContentDescription(
                    title = "Look at this!",
                    description = "Some bug is there"
                )
            )
        )

        val project = e.project ?: return
//        val notificationFollowupActions = NotificationFollowupActions(
//            type = "ShowUrl",
//            content = NotificationFollowupActionsContent(
//                NotificationActionDescription(
//                    title = "Learn more",
//                    url = "https://github.com/aws/aws-toolkit-jetbrains"
//                )
//            )
//        )

        val a = ProcessNotificationsBase.getInstance(project)
        a.processNotification(project, notificationWithValidConnectionData)
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = Registry.`is`("aws.toolkit.developerMode")
    }
}
