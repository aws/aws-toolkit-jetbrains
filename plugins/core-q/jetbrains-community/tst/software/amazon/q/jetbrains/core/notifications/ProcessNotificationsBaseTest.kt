// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.amazon.q.jetbrains.core.notifications

import com.intellij.openapi.project.Project
import com.intellij.testFramework.ApplicationExtension
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.spyk
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.util.concurrent.atomic.AtomicBoolean

@ExtendWith(ApplicationExtension::class)
class ProcessNotificationsBaseTest {
    private lateinit var sut: ProcessNotificationsBase
    private lateinit var project: Project
    private lateinit var dismissalState: NotificationDismissalState

    @BeforeEach
    fun setUp() {
        project = mockk()
        dismissalState = spyk(NotificationDismissalState())

        mockkObject(NotificationDismissalState)
        every { NotificationDismissalState.getInstance() } returns dismissalState

        sut = spyk<ProcessNotificationsBase>(
            objToCopy = ProcessNotificationsBase(project)
        )
    }

    @Test
    fun `startup notifications are only processed on first poll`() {
        resetIsStartup()
        val startupNotification = createNotification("startup-1", NotificationScheduleType.STARTUP)
        every { sut["getNotificationsFromFile"]() } returns createNotificationsList(startupNotification)
        every { dismissalState.isDismissed(any()) } returns false

        sut.retrieveStartupAndEmergencyNotifications()

        verify(exactly = 1) { sut.processNotification(project, startupNotification) }

        // Second poll
        sut.retrieveStartupAndEmergencyNotifications()

        // Verify processNotification wasn't called again
        verify(exactly = 1) { sut.processNotification(project, any()) }
    }

    @Test
    fun `non startup notifications are processed on every poll`() {
        val emergencyNotification = createNotification("emergency-1", NotificationScheduleType.EMERGENCY)
        every { sut["getNotificationsFromFile"]() } returns createNotificationsList(emergencyNotification)
        every { dismissalState.isDismissed(any()) } returns false

        // First poll
        sut.retrieveStartupAndEmergencyNotifications()
        // Second poll
        sut.retrieveStartupAndEmergencyNotifications()

        verify(exactly = 2) { sut.processNotification(project, emergencyNotification) }
    }

    @Test
    fun `dismissed notifications are not processed`() {
        val notification = createNotification("toBeDismissed-1", NotificationScheduleType.EMERGENCY)
        every { sut["getNotificationsFromFile"]() } returns createNotificationsList(notification)

        // first poll results in showing/dismissal
        sut.retrieveStartupAndEmergencyNotifications()
        NotificationDismissalState.getInstance().dismissNotification(notification.id)

        // second poll skips processing
        sut.retrieveStartupAndEmergencyNotifications()

        verify(exactly = 1) { sut.processNotification(project, any()) }
    }

    @Test
    fun `null notifications list is handled gracefully`() {
        every { sut["getNotificationsFromFile"]() } returns null

        sut.retrieveStartupAndEmergencyNotifications()

        verify(exactly = 0) { sut.processNotification(project, any()) }
    }

    @Test
    fun `empty notifications list is handled gracefully`() {
        every { sut["getNotificationsFromFile"]() } returns createNotificationsList()

        sut.retrieveStartupAndEmergencyNotifications()

        verify(exactly = 0) { sut.processNotification(project, any()) }
    }

    @Test
    fun `multiple notifications are processed correctly`() {
        val startupNotification = createNotification("startup-1", NotificationScheduleType.STARTUP)
        val emergencyNotification = createNotification("emergency-1", NotificationScheduleType.EMERGENCY)

        every { sut["getNotificationsFromFile"]() } returns createNotificationsList(
            startupNotification,
            emergencyNotification
        )
        every { dismissalState.isDismissed(any()) } returns false

        // First poll - both should be processed
        sut.retrieveStartupAndEmergencyNotifications()

        verify(exactly = 1) { sut.processNotification(project, startupNotification) }
        verify(exactly = 1) { sut.processNotification(project, emergencyNotification) }

        // Second poll - only emergency should be processed
        sut.retrieveStartupAndEmergencyNotifications()

        verify(exactly = 1) { sut.processNotification(project, startupNotification) }
        verify(exactly = 2) { sut.processNotification(project, emergencyNotification) }
    }

    // Helper functions to create test data
    private fun createNotification(id: String, type: NotificationScheduleType) = NotificationData(
        id = id,
        schedule = NotificationSchedule(type = type),
        severity = "INFO",
        condition = null,
        content = NotificationContentDescriptionLocale(
            NotificationContentDescription(
                title = "Look at this!",
                description = "Some bug is there"
            )
        ),
        actions = emptyList()
    )

    private fun createNotificationsList(vararg notifications: NotificationData) = NotificationsList(
        schema = Schema("1.0"),
        notifications = notifications.toList()
    )

    private fun resetIsStartup() {
        val clazz = Class.forName("software.amazon.q.jetbrains.core.notifications.ProcessNotificationsBaseKt")
        val field = clazz.getDeclaredField("isStartup")
        field.isAccessible = true

        val value = field.get(null) as AtomicBoolean
        value.set(true)
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }
}
