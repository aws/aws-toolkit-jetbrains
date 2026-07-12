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
    fun `formatFailureReasons returns empty string when null`() {
        assertThat(service().formatFailureReasons(null)).isEmpty()
    }

    @Test
    fun `formatFailureReasons returns empty string when empty`() {
        assertThat(service().formatFailureReasons(emptyMap())).isEmpty()
    }

    @Test
    fun `formatFailureReasons returns empty string when inner maps are empty`() {
        val reasons = mapOf("AWS::S3::Bucket" to emptyMap<String, String>())
        assertThat(service().formatFailureReasons(reasons)).isEmpty()
    }

    @Test
    fun `formatFailureReasons formats single reason`() {
        val reasons = mapOf("AWS::S3::Bucket" to mapOf("my-bucket" to "ResourceNotFoundException: bucket not found"))
        assertThat(service().formatFailureReasons(reasons)).isEqualTo("[ResourceNotFoundException: bucket not found]")
    }

    @Test
    fun `formatFailureReasons shows up to the display limit without truncation`() {
        val reasons = mapOf(
            "AWS::S3::Bucket" to linkedMapOf(
                "bucket-1" to "ResourceNotFoundException: bucket-1 not found",
                "bucket-2" to "AccessDeniedException: bucket-2 not authorized"
            )
        )
        assertThat(service().formatFailureReasons(reasons))
            .isEqualTo("[ResourceNotFoundException: bucket-1 not found], [AccessDeniedException: bucket-2 not authorized]")
    }

    @Test
    fun `formatFailureReasons truncates beyond the display limit and summarizes the remainder`() {
        val identifiers = (1..5).associate { "bucket-$it" to "ResourceNotFoundException: bucket-$it not found" }
        val reasons = linkedMapOf("AWS::S3::Bucket" to identifiers)
        val result = service().formatFailureReasons(reasons)
        // only the first 2 shown, remaining 3 summarized
        assertThat(result).startsWith("[ResourceNotFoundException: bucket-1 not found], [ResourceNotFoundException: bucket-2 not found]")
        assertThat(result).doesNotContain("bucket-3 not found")
        assertThat(result).endsWith("and 3 more")
    }

    @Test
    fun `formatFailureReasons truncates across resource types`() {
        val reasons = linkedMapOf(
            "AWS::S3::Bucket" to linkedMapOf(
                "b1" to "ResourceNotFoundException: b1 not found",
                "b2" to "ResourceNotFoundException: b2 not found"
            ),
            "AWS::Lambda::Function" to mapOf("f1" to "AccessDeniedException: f1 not authorized")
        )
        val result = service().formatFailureReasons(reasons)
        assertThat(result).startsWith("[ResourceNotFoundException: b1 not found], [ResourceNotFoundException: b2 not found]")
        assertThat(result).endsWith("and 1 more")
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
