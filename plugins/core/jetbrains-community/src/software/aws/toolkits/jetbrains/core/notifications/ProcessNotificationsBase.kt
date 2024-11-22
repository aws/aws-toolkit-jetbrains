// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.core.notifications

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.project.Project
import software.aws.toolkits.core.utils.inputStream
import java.nio.file.Paths

object NotificationMapperUtil {
    val mapper = jacksonObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
}

class ProcessNotificationsBase {

    init {
        NotificationPollingService.getInstance().addObserver {
            val list = getNotificationsFromFile()
            println("here")
        }
    }

    fun getNotificationsFromFile(): NotificationsList? {
        val path = Paths.get(PathManager.getSystemPath(), NOTIFICATIONS_PATH)
        val content = path.inputStream().bufferedReader().use { it.readText() }
        if (content.isEmpty()) {
            return null
        }
        return NotificationMapperUtil.mapper.readValue(content)
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

    companion object {
        private const val NOTIFICATIONS_PATH = "aws-static-resources/notifications.json"
    }
}
