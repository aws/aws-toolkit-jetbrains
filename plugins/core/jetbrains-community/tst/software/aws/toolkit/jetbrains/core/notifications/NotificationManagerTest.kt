// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkit.jetbrains.core.notifications

import com.intellij.testFramework.ApplicationExtension
import com.intellij.testFramework.ProjectRule
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extension
import org.junit.jupiter.api.extension.RegisterExtension
import software.aws.toolkit.jetbrains.core.notifications.NotificationActionDescription
import software.aws.toolkit.jetbrains.core.notifications.NotificationFollowupActions
import software.aws.toolkit.jetbrains.core.notifications.NotificationFollowupActionsContent
import software.aws.toolkit.jetbrains.core.notifications.NotificationManager

@ExtendWith(ApplicationExtension::class)
class NotificationManagerTest {

    val projectRule = ProjectRule()

    @JvmField
    @RegisterExtension
    val testExtension = object : Extension {
        fun getProject() = projectRule.project
    }

    @Test
    fun `If no follow-up actions, expand action is present`() {
        val sut = NotificationManager.createActions(projectRule.project, listOf(), "Dummy Test Action", "Dummy title")
        assertThat(sut).isNotNull
        assertThat(sut).hasSize(1)
        assertThat(sut.first().title).isEqualTo("More...")
    }

    @Test
    fun `Show Url action shows the option to learn more`() {
        val followupActions = NotificationFollowupActions(
            "UpdateExtension",
            NotificationFollowupActionsContent(NotificationActionDescription("title", null))
        )
        val sut = NotificationManager.createActions(projectRule.project, listOf(followupActions), "Dummy Test Action", "Dummy title")
        assertThat(sut).isNotNull
        assertThat(sut).hasSize(2)
        assertThat(sut.first().title).isEqualTo("Update")
        assertThat(sut[1].title).isEqualTo("More...")
    }
}
