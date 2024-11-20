// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.core.notifications

class ProcessNotificationsBase {
    init {
        // TODO: install a listener for the polling class
    }

    fun getNotificationsFromFile() {
        // TODO: returns a notification list
    }

    fun retrieveStartupAndEmergencyNotifications() {
        // TODO: separates notifications into startup and emergency
        // iterates through the 2 lists and processes each notification(if it isn't dismissed)
    }

    fun processNotification() {
        // TODO: calls the Rule engine and notifies listeners
    }

    fun notifyListenerForNotification() {
    }

    fun addListenerForNotification() {
    }
}
