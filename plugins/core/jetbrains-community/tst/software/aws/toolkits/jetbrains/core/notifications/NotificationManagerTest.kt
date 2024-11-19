// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.core.notifications

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class NotificationManagerTest {

    @Test
    fun `If no follow-up actions, expand action is present`() {
        val sut = NotificationManager.createActions(null, "Dummy Test Action", "Dummy title")
        assertThat(sut).isNotNull
        assertThat(sut).hasSize(1)
        assertThat(sut.first().title).isEqualTo("Expand")
    }

    @Test
    fun `Show Url action shows the option to learn more`() {
        val followupActions = NotificationFollowupActions(
            "ShowUrl",
            NotificationFollowupActionsContent(NotificationActionDescription("title", "http://leadsnowhere"))
        )
        val sut = NotificationManager.createActions(listOf(followupActions), "Dummy Test Action", "Dummy title")
        assertThat(sut).isNotNull
        assertThat(sut).hasSize(2)
        assertThat(sut.first().title).isEqualTo("Expand")
        assertThat(sut[1].title).isEqualTo("Learn more")
    }
}
