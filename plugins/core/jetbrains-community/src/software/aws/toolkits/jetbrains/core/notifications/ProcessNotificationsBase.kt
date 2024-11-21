// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.core.notifications

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.intellij.openapi.project.Project
import software.aws.toolkits.core.utils.inputStream
import java.nio.file.Path

object NotificationMapperUtil {
    val mapper = jacksonObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
}

class ProcessNotificationsBase {
    init {
        NotificationPollingServiceImpl.getInstance().addObserver { path ->
            getNotificationsFromFile(path)
        }
    }

    fun getNotificationsFromFile(path: Path): NotificationsList =
        path.inputStream().use { data ->
            NotificationMapperUtil.mapper.readValue<NotificationsList>(data)
        }

    fun retrieveStartupAndEmergencyNotifications() {
        // TODO: separates notifications into startup and emergency
        // iterates through the 2 lists and processes each notification(if it isn't dismissed)
    }

    fun processNotification(project: Project, notificationData: NotificationData) {
        val shouldShow = RulesEngine.displayNotification(project, notificationData)
        if (shouldShow) {
            // TODO: notifies listeners
        }
    }

    fun notifyListenerForNotification() {
    }

    fun addListenerForNotification() {
    }
}
