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
        val reasons = mapOf("AWS::S3::Bucket" to mapOf("my-bucket" to "Resource not found"))
        assertThat(service().formatFailureReasons(reasons)).isEqualTo(": [my-bucket: Resource not found]")
    }

    @Test
    fun `formatFailureReasons formats multiple identifiers within a type`() {
        val reasons = mapOf(
            "AWS::S3::Bucket" to linkedMapOf("bucket-1" to "Not found", "bucket-2" to "Permission denied")
        )
        assertThat(service().formatFailureReasons(reasons))
            .isEqualTo(": [bucket-1: Not found], [bucket-2: Permission denied]")
    }

    @Test
    fun `formatFailureReasons formats reasons across multiple types`() {
        val reasons = linkedMapOf(
            "AWS::S3::Bucket" to mapOf("my-bucket" to "Resource not found"),
            "AWS::Lambda::Function" to mapOf("my-func" to "Access denied")
        )
        assertThat(service().formatFailureReasons(reasons))
            .isEqualTo(": [my-bucket: Resource not found], [my-func: Access denied]")
    }

    @Test
    fun `partial failure notification includes reasons suffix`() {
        val reasons = mapOf("AWS::S3::Bucket" to mapOf("my-bucket" to "Not found"))
        service().showResultNotification(1, 1, ResourceStatePurpose.IMPORT, reasons)

        assertThat(notifications).hasSize(1)
        assertThat(notifications[0].type).isEqualTo(NotificationType.WARNING)
        assertThat(notifications[0].content).contains("[my-bucket: Not found]")
    }

    @Test
    fun `full failure notification includes reasons suffix`() {
        val reasons = mapOf("AWS::S3::Bucket" to mapOf("my-bucket" to "Access denied"))
        service().showResultNotification(0, 1, ResourceStatePurpose.IMPORT, reasons)

        assertThat(notifications).hasSize(1)
        assertThat(notifications[0].type).isEqualTo(NotificationType.ERROR)
        assertThat(notifications[0].content).contains("[my-bucket: Access denied]")
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
        val reasons = mapOf("AWS::S3::Bucket" to mapOf("my-bucket" to "Access denied"))
        service().showResultNotification(0, 1, ResourceStatePurpose.CLONE, reasons)

        assertThat(notifications).hasSize(1)
        assertThat(notifications[0].type).isEqualTo(NotificationType.ERROR)
        assertThat(notifications[0].content).contains("clone")
        assertThat(notifications[0].content).contains("[my-bucket: Access denied]")
    }
}
