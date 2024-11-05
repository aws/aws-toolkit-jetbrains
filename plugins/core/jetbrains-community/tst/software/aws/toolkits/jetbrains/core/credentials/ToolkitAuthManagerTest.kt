// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.core.credentials

import com.intellij.notification.Notifications
import com.intellij.openapi.project.Project
import com.intellij.testFramework.ApplicationExtension
import com.intellij.util.messages.MessageBus
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.runs
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith
import software.aws.toolkits.jetbrains.core.credentials.sso.DeviceAuthorizationGrantToken
import software.aws.toolkits.jetbrains.core.credentials.sso.bearer.BearerTokenAuthState
import software.aws.toolkits.jetbrains.core.credentials.sso.bearer.BearerTokenProvider
import software.aws.toolkits.jetbrains.core.credentials.sso.bearer.BearerTokenProviderListener
import software.aws.toolkits.jetbrains.utils.notifyInfo
import software.aws.toolkits.resources.AwsCoreBundle.message
import java.net.UnknownHostException
import java.time.Instant
import java.time.temporal.ChronoUnit

@ExtendWith(ApplicationExtension::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ToolkitAuthManagerTest {
    private lateinit var project: Project
    private lateinit var tokenProvider: BearerTokenProvider
    private var reauthCallCount = 0

    @BeforeEach
    fun setUp() {
        project = mockk()
        tokenProvider = mockk()
        reauthCallCount = 0

        // Mock MessageBus and Notifications
        val messageBus = mockk<MessageBus>(relaxed = true)
        val notificationsPublisher = mockk<Notifications>(relaxed = true)

        every { project.messageBus } returns messageBus
        every { messageBus.syncPublisher(Notifications.TOPIC) } returns notificationsPublisher
        every { notificationsPublisher.notify(any()) } just runs

        // Mock BearerTokenProvider methods
        every { tokenProvider.id } returns "mockProviderId"

        // Mock static method
        mockkObject(BearerTokenProviderListener)
        mockkStatic("software.aws.toolkits.jetbrains.utils.NotificationUtilsKt")
        every {
            notifyInfo(any(), any(), any())
        } just runs
        every { BearerTokenProviderListener.notifyCredUpdate(any<String>()) } just runs
        resetNetworkErrorState()
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `test NEEDS_REFRESH state with network error - first occurrence`() {
        every { tokenProvider.state() } returns BearerTokenAuthState.NEEDS_REFRESH
        every { tokenProvider.resolveToken() } throws UnknownHostException("Unable to execute HTTP request")

        try {
            maybeReauthProviderIfNeeded(
                project,
                ReauthSource.TOOLKIT,
                tokenProvider
            ) { _ -> reauthCallCount++ }
        } catch (e: UnknownHostException) {
            // ignore
        }
        assertEquals(0, reauthCallCount)
        verify(exactly = 1) {
            notifyInfo(
                message("general.auth.network.error"),
                message("general.auth.network.error.message"),
                project
            )
        }
    }

    @Test
    fun `test NEEDS_REFRESH state with network error - subsequent occurrence`() {
        every { tokenProvider.state() } returns BearerTokenAuthState.NEEDS_REFRESH
        every { tokenProvider.resolveToken() } throws UnknownHostException("Unable to execute HTTP request")

        // First call to set the internal flag
        try {
            maybeReauthProviderIfNeeded(
                project,
                ReauthSource.TOOLKIT,
                tokenProvider
            ) { _ -> reauthCallCount++ }
        } catch (e: UnknownHostException) {
            // ignore
        }

        // Second call - should not show notification
        try {
            maybeReauthProviderIfNeeded(
                project,
                ReauthSource.TOOLKIT,
                tokenProvider
            ) { _ -> reauthCallCount++ }
        } catch (e: UnknownHostException) {
            // ignore
        }

        assertEquals(0, reauthCallCount)
        verify(exactly = 1) {
            notifyInfo(
                message("general.auth.network.error"),
                message("general.auth.network.error.message"),
                project
            )
        }
    }

    @Test
    fun `test successful refresh clears notification flag`() {
        every { tokenProvider.state() } returns BearerTokenAuthState.NEEDS_REFRESH

        // First trigger a network error
        every { tokenProvider.resolveToken() } throws UnknownHostException("Unable to execute HTTP request")

        try {
            maybeReauthProviderIfNeeded(
                project,
                ReauthSource.TOOLKIT,
                tokenProvider
            ) { _ -> reauthCallCount++ }
        } catch (e: UnknownHostException) {
            // ignore
        }

        // Now simulate successful refresh
        every { tokenProvider.state() } returns BearerTokenAuthState.NEEDS_REFRESH
        every { tokenProvider.resolveToken() } returns DeviceAuthorizationGrantToken(
            startUrl = "https://example.com",
            region = "us-east-1",
            accessToken = "testAccessToken",
            refreshToken = "testRefreshToken",
            expiresAt = Instant.now().plus(1, ChronoUnit.HOURS),
        )
        maybeReauthProviderIfNeeded(
            project,
            ReauthSource.TOOLKIT,
            tokenProvider
        ) { _ -> reauthCallCount++ }

        // Now trigger another network error - should show notification again
        every { tokenProvider.state() } returns BearerTokenAuthState.NEEDS_REFRESH
        every { tokenProvider.resolveToken() } throws UnknownHostException("Unable to execute HTTP request")
        try {
            maybeReauthProviderIfNeeded(
                project,
                ReauthSource.TOOLKIT,
                tokenProvider
            ) { _ -> reauthCallCount++ }
        } catch (e: UnknownHostException) {
            // ignore
        }

        verify(exactly = 2) {
            notifyInfo(
                message("general.auth.network.error"),
                message("general.auth.network.error.message"),
                project
            )
        }
    }
}
