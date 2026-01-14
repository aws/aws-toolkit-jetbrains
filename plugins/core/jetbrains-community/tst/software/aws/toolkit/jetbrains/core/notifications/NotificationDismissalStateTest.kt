// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkit.jetbrains.core.notifications

import org.assertj.core.api.Assertions.assertThat
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

        assertThat(persistedState.dismissedNotifications)
            .singleElement()
            .extracting(DismissedNotification::id)
            .isEqualTo("recent-notification")

        assertThat(state.isDismissed("recent-notification")).isTrue()
    }

    @Test
    fun `notifications older than 2 months are removed`() {
        val oldNotification = DismissedNotification(
            id = "old-notification",
            dismissedAt = Instant.now().minus(61, ChronoUnit.DAYS).toEpochMilli().toString()
        )

        state.loadState(NotificationDismissalConfiguration(mutableSetOf(oldNotification)))

        val persistedState = state.getState()

        assertThat(persistedState.dismissedNotifications).isEmpty()
        assertThat(state.isDismissed("old-notification")).isFalse()
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

        assertThat(persistedState.dismissedNotifications).hasSize(1)
        assertThat(state.isDismissed("recent-notification")).isTrue()
        assertThat(state.isDismissed("old-notification")).isFalse()
    }

    @Test
    fun `dismissing new notification retains it`() {
        state.dismissNotification("new-notification")

        assertThat(state.isDismissed("new-notification")).isTrue()
    }
}
