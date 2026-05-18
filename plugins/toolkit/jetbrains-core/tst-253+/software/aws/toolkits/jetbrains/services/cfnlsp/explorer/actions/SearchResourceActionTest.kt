// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cfnlsp.explorer.actions

import com.intellij.notification.Notification
import com.intellij.notification.Notifications
import com.intellij.testFramework.ProjectRule
import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import software.aws.toolkits.jetbrains.services.cfnlsp.protocol.SearchResourceResult

class SearchResourceActionTest {

    @get:Rule
    val projectRule = ProjectRule()

    private val notifications = mutableListOf<Notification>()

    @Before
    fun setUp() {
        projectRule.project.messageBus.connect().subscribe(
            Notifications.TOPIC,
            object : Notifications {
                override fun notify(notification: Notification) {
                    notifications.add(notification)
                }
            }
        )
    }

    @After
    fun tearDown() {
        notifications.clear()
    }

    @Test
    fun `handleSearchResult shows notification when result is null`() {
        SearchResourceAction.handleSearchResult(null, "asdf", "AWS::IAM::Role", projectRule.project)

        assertThat(notifications).hasSize(1)
        assertThat(notifications[0].content).contains("asdf")
        assertThat(notifications[0].content).contains("AWS::IAM::Role")
    }

    @Test
    fun `handleSearchResult shows notification with error detail when not found`() {
        val result = SearchResourceResult(found = false, resource = null, error = "Resource not found")

        SearchResourceAction.handleSearchResult(result, "asdf", "AWS::IAM::Role", projectRule.project)

        assertThat(notifications).hasSize(1)
        assertThat(notifications[0].content).contains("asdf")
        assertThat(notifications[0].content).contains("Resource not found")
    }

    @Test
    fun `handleSearchResult shows generic notification when not found without error`() {
        val result = SearchResourceResult(found = false, resource = null)

        SearchResourceAction.handleSearchResult(result, "asdf", "AWS::IAM::Role", projectRule.project)

        assertThat(notifications).hasSize(1)
        assertThat(notifications[0].content).contains("asdf")
        assertThat(notifications[0].content).contains("AWS::IAM::Role")
    }

    @Test
    fun `handleSearchResult does not show notification when resource found`() {
        val result = SearchResourceResult(found = true, resource = null)

        SearchResourceAction.handleSearchResult(result, "my-role", "AWS::IAM::Role", projectRule.project)

        assertThat(notifications).isEmpty()
    }
}
