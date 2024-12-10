// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.core.notifications

import org.junit.jupiter.api.Assertions.*
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
        // Given
        val recentNotification = DismissedNotification(
            id = "recent-notification",
            dismissedAt = Instant.now().minus(30, ChronoUnit.DAYS)
        )

        state.loadState(NotificationDismissalConfiguration(mutableSetOf(recentNotification)))

        // When
        val persistedState = state.getState()

        // Then
        assertEquals(1, persistedState.dismissedNotifications.size)
        assertTrue(persistedState.dismissedNotifications.any { it.id == "recent-notification" })
        assertTrue(state.isDismissed("recent-notification"))
    }

    @Test
    fun `notifications older than 2 months are removed`() {
        // Given
        val oldNotification = DismissedNotification(
            id = "old-notification",
            dismissedAt = Instant.now().minus(61, ChronoUnit.DAYS)
        )

        state.loadState(NotificationDismissalConfiguration(mutableSetOf(oldNotification)))

        // When/Then
        assertFalse(state.isDismissed("old-notification"))
    }

    @Test
    fun `mixed age notifications are handled correctly`() {
        // Given
        val recentNotification = DismissedNotification(
            id = "recent-notification",
            dismissedAt = Instant.now().minus(30, ChronoUnit.DAYS)
        )
        val oldNotification = DismissedNotification(
            id = "old-notification",
            dismissedAt = Instant.now().minus(61, ChronoUnit.DAYS)
        )

        state.loadState(
            NotificationDismissalConfiguration(
                mutableSetOf(recentNotification, oldNotification)
            )
        )

        // When/Then
        assertTrue(state.isDismissed("recent-notification"))
        assertFalse(state.isDismissed("old-notification"))
    }

    @Test
    fun `dismissing new notification retains it`() {
        // When
        state.dismissNotification("new-notification")

        // Then
        assertTrue(state.isDismissed("new-notification"))
    }

    @Test
    fun `state is correctly persisted`() {
        // Given
        val notification = DismissedNotification(
            id = "test-notification",
            dismissedAt = Instant.now()
        )

        // When
        state.loadState(NotificationDismissalConfiguration(mutableSetOf(notification)))
        val persistedState = state.getState()

        // Then
        assertTrue(persistedState.dismissedNotifications.any { it.id == "test-notification" })
    }

    @Test
    fun `clean up happens on load state`() {
        // Given
        val oldNotification = DismissedNotification(
            id = "old-notification",
            dismissedAt = Instant.now().minus(61, ChronoUnit.DAYS)
        )
        val recentNotification = DismissedNotification(
            id = "recent-notification",
            dismissedAt = Instant.now()
        )

        // When
        state.loadState(
            NotificationDismissalConfiguration(
                mutableSetOf(oldNotification, recentNotification)
            )
        )

        // Then
        val persistedState = state.getState()
        assertEquals(1, persistedState.dismissedNotifications.size)
        assertTrue(persistedState.dismissedNotifications.any { it.id == "recent-notification" })
    }
}
