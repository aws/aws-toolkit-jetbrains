// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.core.notifications

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.temporal.ChronoUnit

class NotificationDismissalStateTest {
    private lateinit var state: NotificationDismissalState

    @BeforeEach
    fun setUp() {
        state = NotificationDismissalState()
    }

    @Test
    fun `notifications less than 2 months old are not removed`() {
        val recentNotification = DismissedNotification(
            id = "recent-notification",
            dismissedAt = Instant.now().minus(30, ChronoUnit.DAYS).toEpochMilli().toString()
        )

        state.loadState(NotificationDismissalConfiguration(mutableSetOf(recentNotification)))

        val persistedState = state.getState()

        assertEquals(1, persistedState.dismissedNotifications.size)
        assertTrue(persistedState.dismissedNotifications.any { it.id == "recent-notification" })
        assertTrue(state.isDismissed("recent-notification"))
    }

    @Test
    fun `notifications older than 2 months are removed`() {
        val oldNotification = DismissedNotification(
            id = "old-notification",
            dismissedAt = Instant.now().minus(61, ChronoUnit.DAYS).toEpochMilli().toString()
        )

        state.loadState(NotificationDismissalConfiguration(mutableSetOf(oldNotification)))

        val persistedState = state.getState()

        assertEquals(0, persistedState.dismissedNotifications.size)
        assertFalse(state.isDismissed("old-notification"))
    }

    @Test
    fun `mixed age notifications are handled correctly`() {
        val recentNotification = DismissedNotification(
            id = "recent-notification",
            dismissedAt = Instant.now().minus(30, ChronoUnit.DAYS).toEpochMilli().toString()
        )
        val oldNotification = DismissedNotification(
            id = "old-notification",
            dismissedAt = Instant.now().minus(61, ChronoUnit.DAYS).toEpochMilli().toString()
        )

        state.loadState(
            NotificationDismissalConfiguration(
                mutableSetOf(recentNotification, oldNotification)
            )
        )

        val persistedState = state.getState()

        assertEquals(1, persistedState.dismissedNotifications.size)
        assertTrue(state.isDismissed("recent-notification"))
        assertFalse(state.isDismissed("old-notification"))
    }

    @Test
    fun `dismissing new notification retains it`() {
        state.dismissNotification("new-notification")

        assertTrue(state.isDismissed("new-notification"))
    }
}
