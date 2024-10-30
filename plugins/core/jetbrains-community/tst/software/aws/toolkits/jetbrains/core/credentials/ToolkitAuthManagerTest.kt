// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.core.credentials

import com.intellij.openapi.project.Project
import com.intellij.testFramework.ApplicationExtension
import io.mockk.clearAllMocks
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import io.mockk.mockkStatic
import io.mockk.verify
import io.mockk.every
import io.mockk.just
import io.mockk.mockkObject
import io.mockk.runs
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.reset
import software.amazon.awssdk.auth.token.credentials.SdkToken
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
        project = mock()
        tokenProvider = mock()
        reauthCallCount = 0
        val field = Class.forName("software.aws.toolkits.jetbrains.core.credentials.ToolkitAuthManagerKt")
            .getDeclaredField("hasNotifiedNetworkErrorOnce")
        field.isAccessible = true
        field.set(null, false)

        mockkObject(BearerTokenProviderListener)
        mockkStatic("software.aws.toolkits.jetbrains.utils.NotificationUtilsKt")
        every {
            notifyInfo(any(), any(), any())
        } just runs
        every { BearerTokenProviderListener.notifyCredUpdate(any<String>()) } just runs
    }


    @Test
    fun `test NEEDS_REFRESH state with network error - first occurrence`() {
        whenever(tokenProvider.state()).thenReturn(BearerTokenAuthState.NEEDS_REFRESH)
        doThrow(RuntimeException("Unable to execute HTTP request"))
            .`when`(tokenProvider)
            .resolveToken()

        val result = maybeReauthProviderIfNeeded(
            project,
            ReauthSource.TOOLKIT,
            tokenProvider
        ) { _ -> reauthCallCount++ }

        assertFalse(result)
        assertEquals(0, reauthCallCount)
        verify(exactly = 1){
            notifyInfo(
                message("general.auth.network.error"),
                message("general.auth.network.error.message"),
                project
            )
        }
    }

    @Test
    fun `test NEEDS_REFRESH state with network error - subsequent occurrence`() {
        whenever(tokenProvider.state()).thenReturn(BearerTokenAuthState.NEEDS_REFRESH)
        doThrow(RuntimeException("Unable to execute HTTP request"))
            .`when`(tokenProvider)
            .resolveToken()

        // First call to set the internal flag
        maybeReauthProviderIfNeeded(
            project,
            ReauthSource.TOOLKIT,
            tokenProvider
        ) { _ -> reauthCallCount++ }

        // Second call - should not show notification
        val result = maybeReauthProviderIfNeeded(
            project,
            ReauthSource.TOOLKIT,
            tokenProvider
        ) { _ -> reauthCallCount++ }

        assertFalse(result)
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
        whenever(tokenProvider.state()).thenReturn(BearerTokenAuthState.NEEDS_REFRESH)

        // First trigger a network error
        doThrow(RuntimeException("Unable to execute HTTP request"))
            .`when`(tokenProvider)
            .resolveToken()

        maybeReauthProviderIfNeeded(
            project,
            ReauthSource.TOOLKIT,
            tokenProvider
        ) { _ -> reauthCallCount++ }

        reset(tokenProvider)

        // Now simulate successful refresh
        whenever(tokenProvider.state()).thenReturn(BearerTokenAuthState.NEEDS_REFRESH)
        whenever(tokenProvider.resolveToken()).thenReturn(
            DeviceAuthorizationGrantToken(
                startUrl = "https://example.com",
                region = "us-east-1",
                accessToken = "testAccessToken",
                refreshToken = "testRefreshToken",
                expiresAt = Instant.now().plus(1, ChronoUnit.HOURS),
            )
        )
        maybeReauthProviderIfNeeded(
            project,
            ReauthSource.TOOLKIT,
            tokenProvider
        ) { _ -> reauthCallCount++ }

        reset(tokenProvider)
        // Now trigger another network error - should show notification again
        whenever(tokenProvider.state()).thenReturn(BearerTokenAuthState.NEEDS_REFRESH)
        doThrow(RuntimeException("Unable to execute HTTP request"))
            .`when`(tokenProvider)
            .resolveToken()
        maybeReauthProviderIfNeeded(
            project,
            ReauthSource.TOOLKIT,
            tokenProvider
        ) { _ -> reauthCallCount++ }

        verify(exactly = 2) {
            notifyInfo(
                message("general.auth.network.error"),
                message("general.auth.network.error.message"),
                project
            )
        }
    }
}
