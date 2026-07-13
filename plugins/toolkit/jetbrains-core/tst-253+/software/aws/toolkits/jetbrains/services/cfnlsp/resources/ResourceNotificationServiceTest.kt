// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cfnlsp.resources

import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.testFramework.ProjectRule
import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import software.aws.toolkits.jetbrains.services.cfnlsp.protocol.ResourceStatePurpose

class ResourceNotificationServiceTest {

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

    private fun service() = ResourceNotificationService(projectRule.project)

    @Test
    fun `notification omits reasons suffix when failureReasons is empty`() {
        service().showResultNotification(0, 1, ResourceStatePurpose.IMPORT, emptyMap())

        assertThat(notifications).hasSize(1)
        assertThat(notifications[0].content).doesNotContain("[")
    }

    @Test
    fun `notification omits reasons suffix when inner maps are empty`() {
        service().showResultNotification(0, 1, ResourceStatePurpose.IMPORT, mapOf("AWS::S3::Bucket" to emptyMap<String, String>()))

        assertThat(notifications).hasSize(1)
        assertThat(notifications[0].content).doesNotContain("[")
    }

    @Test
    fun `notification shows reasons up to the display limit without truncation`() {
        val reasons = mapOf(
            "AWS::S3::Bucket" to linkedMapOf(
                "bucket-1" to "ResourceNotFoundException: bucket-1 not found",
                "bucket-2" to "AccessDeniedException: bucket-2 not authorized"
            )
        )
        service().showResultNotification(0, 2, ResourceStatePurpose.IMPORT, reasons)

        val content = notifications.single().content
        assertThat(content).contains(
            "[ResourceNotFoundException: bucket-1 not found]",
            "[AccessDeniedException: bucket-2 not authorized]"
        )
        assertThat(content).doesNotContain("more")
    }

    @Test
    fun `notification truncates reasons beyond the display limit and summarizes the remainder`() {
        val identifiers = (1..5).associate { "bucket-$it" to "ResourceNotFoundException: bucket-$it not found" }
        val reasons = linkedMapOf("AWS::S3::Bucket" to identifiers)
        service().showResultNotification(0, 5, ResourceStatePurpose.IMPORT, reasons)

        val content = notifications.single().content
        // only the first 2 shown, remaining 3 summarized
        assertThat(content).contains(
            "[ResourceNotFoundException: bucket-1 not found]",
            "[ResourceNotFoundException: bucket-2 not found]"
        )
        assertThat(content).doesNotContain("bucket-3 not found")
        assertThat(content).contains("and 3 more")
    }

    @Test
    fun `partial failure notification includes reasons suffix`() {
        val reasons = mapOf("AWS::S3::Bucket" to mapOf("my-bucket" to "ResourceNotFoundException: not found"))
        service().showResultNotification(1, 1, ResourceStatePurpose.IMPORT, reasons)

        assertThat(notifications).hasSize(1)
        assertThat(notifications[0].type).isEqualTo(NotificationType.WARNING)
        assertThat(notifications[0].content).contains("[ResourceNotFoundException: not found]")
        assertThat(notifications[0].actions).isNotEmpty()
    }

    @Test
    fun `full failure notification includes reasons suffix and a view-log action`() {
        val reasons = mapOf("AWS::S3::Bucket" to mapOf("my-bucket" to "AccessDeniedException: not authorized"))
        service().showResultNotification(0, 1, ResourceStatePurpose.IMPORT, reasons)

        assertThat(notifications).hasSize(1)
        assertThat(notifications[0].type).isEqualTo(NotificationType.ERROR)
        assertThat(notifications[0].content).contains("[AccessDeniedException: not authorized]")
        assertThat(notifications[0].actions).isNotEmpty()
    }

    @Test
    fun `full failure notification without reasons has no suffix`() {
        service().showResultNotification(0, 1, ResourceStatePurpose.IMPORT, null)

        assertThat(notifications).hasSize(1)
        assertThat(notifications[0].type).isEqualTo(NotificationType.ERROR)
        assertThat(notifications[0].content).doesNotContain("[")
    }

    @Test
    fun `success notification does not include reasons suffix`() {
        service().showResultNotification(2, 0, ResourceStatePurpose.IMPORT, null)

        assertThat(notifications).hasSize(1)
        assertThat(notifications[0].type).isEqualTo(NotificationType.INFORMATION)
        assertThat(notifications[0].content).doesNotContain("[")
    }

    @Test
    fun `clone full failure notification includes reasons suffix`() {
        val reasons = mapOf("AWS::S3::Bucket" to mapOf("my-bucket" to "AccessDeniedException: not authorized"))
        service().showResultNotification(0, 1, ResourceStatePurpose.CLONE, reasons)

        assertThat(notifications).hasSize(1)
        assertThat(notifications[0].type).isEqualTo(NotificationType.ERROR)
        assertThat(notifications[0].content).contains("clone")
        assertThat(notifications[0].content).contains("[AccessDeniedException: not authorized]")
    }
}
