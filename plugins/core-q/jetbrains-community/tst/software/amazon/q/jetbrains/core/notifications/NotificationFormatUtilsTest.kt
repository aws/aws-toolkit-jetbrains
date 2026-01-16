// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.amazon.q.jetbrains.core.notifications

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.intellij.testFramework.ApplicationExtension
import com.intellij.testFramework.ProjectRule
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.assertj.core.api.Assertions.assertThat
import org.junit.Rule
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import software.amazon.q.core.utils.exists
import software.amazon.q.core.utils.inputStream
import software.amazon.q.jetbrains.core.gettingstarted.editor.BearerTokenFeatureSet
import java.io.InputStream
import java.nio.file.Paths
import java.util.stream.Stream

@ExtendWith(ApplicationExtension::class)
class NotificationFormatUtilsTest {
    @Rule
    @JvmField
    val projectRule = ProjectRule()

    private lateinit var mockSystemDetails: SystemDetails
    private lateinit var mockSystemDetailsWithNoPlugin: SystemDetails
    private lateinit var exampleNotification: InputStream

    @BeforeEach
    fun setUp() {
        mockSystemDetails = SystemDetails(
            computeType = "Local",
            computeArchitecture = "x86_64",
            osType = "Linux",
            osVersion = "5.4.0",
            ideType = "IC",
            ideVersion = "2023.1",
            pluginVersions = mapOf(
                "aws.toolkit" to "1.0",
                "amazon.q" to "2.0"
            )
        )

        mockSystemDetailsWithNoPlugin = SystemDetails(
            computeType = "Local",
            computeArchitecture = "x86_64",
            osType = "Linux",
            osVersion = "5.4.0",
            ideType = "IC",
            ideVersion = "2023.1",
            pluginVersions = mapOf(
                "aws.toolkit" to "1.0",
            )
        )

        exampleNotification = javaClass.getResource("/exampleNotification2.json")?.let {
            Paths.get(it.toURI()).takeIf { f -> f.exists() }
        }?.inputStream() ?: throw RuntimeException("Test not found")

        mockkStatic("software.amazon.q.jetbrains.core.notifications.RulesEngineKt")
        every { getCurrentSystemAndConnectionDetails() } returns mockSystemDetails
        every { getConnectionDetailsForFeature(projectRule.project, BearerTokenFeatureSet.Q) } returns FeatureAuthDetails(
            "Idc",
            "us-west-2",
            "Connected"
        )
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `test System Details`() {
        val result = getCurrentSystemAndConnectionDetails()
        assertThat(mockSystemDetails).isEqualTo(result)
    }

    @Test
    fun `check Json Validity which has all the required fields`() {
        assertDoesNotThrow {
            mapper.readValue<NotificationsList>(exampleNotification)
        }
    }

    @Test
    fun `No schema version associated with the notification file throws an exception`() {
        assertThrows<Exception> {
            mapper.readValue<NotificationsList>(exampleNotificationWithoutSchema)
        }
    }

    @Test
    fun `No notifications present with the version file does not throw an exception`() {
        assertDoesNotThrow {
            mapper.readValue<NotificationsList>(exampleNotificationWithoutNotification)
        }
    }

    @Test
    fun `If plugin is not present, notification is not shown`() {
        every { getCurrentSystemAndConnectionDetails() } returns mockSystemDetailsWithNoPlugin
        val shouldShow = RulesEngine.displayNotification(projectRule.project, pluginNotPresentData)
        assertThat(shouldShow).isFalse
    }

    @ParameterizedTest
    @MethodSource("validNotifications")
    fun `The notification is shown`(notification: String, expectedData: NotificationData) {
        val notificationData = mapper.readValue<NotificationData>(notification)
        assertThat(notificationData).isEqualTo(expectedData)
        val shouldShow = RulesEngine.displayNotification(projectRule.project, notificationData)
        assertThat(shouldShow).isTrue
    }

    @ParameterizedTest
    @MethodSource("invalidNotifications")
    fun `The notification is not shown`(notification: String, expectedData: NotificationData) {
        val notificationData = mapper.readValue<NotificationData>(notification)
        assertThat(notificationData).isEqualTo(expectedData)
        val shouldShow = RulesEngine.displayNotification(projectRule.project, notificationData)
        assertThat(shouldShow).isFalse
    }

    companion object {
        @JvmStatic
        fun validNotifications(): Stream<Arguments> = Stream.of(
            Arguments.of(notificationWithConditionsOrActions, notificationWithConditionsOrActionsData),
            Arguments.of(notificationWithoutConditionsOrActions, notificationsWithoutConditionsOrActionsData),
            Arguments.of(notificationWithValidConnection, notificationWithValidConnectionData)
        )

        @JvmStatic
        fun invalidNotifications(): Stream<Arguments> = Stream.of(
            Arguments.of(validComputeInvalidOs, validOsInvalidComputeData),
            Arguments.of(invalidExtensionVersion, invalidExtensionVersionData),
            Arguments.of(invalidIdeTypeAndVersion, invalidIdeTypeAndVersionData)
        )

        private val mapper = jacksonObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    }
}
