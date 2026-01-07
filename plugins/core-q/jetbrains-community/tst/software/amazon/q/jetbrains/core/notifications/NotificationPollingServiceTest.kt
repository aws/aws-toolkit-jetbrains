// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.amazon.q.jetbrains.core.notifications

import com.intellij.testFramework.ApplicationExtension
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import software.amazon.q.core.utils.RemoteResourceResolver
import software.amazon.q.core.utils.UpdateCheckResult
import software.amazon.q.jetbrains.core.RemoteResourceResolverProvider
import java.nio.file.Path
import java.util.concurrent.CompletableFuture

@ExtendWith(ApplicationExtension::class)
class NotificationPollingServiceTest {
    private lateinit var sut: NotificationPollingService
    private lateinit var mockResolver: RemoteResourceResolver
    private lateinit var mockProvider: RemoteResourceResolverProvider
    private lateinit var observer: () -> Unit
    private val testPath = Path.of("/test/path")

    @BeforeEach
    fun setUp() {
        sut = NotificationPollingService()

        mockResolver = mockk<RemoteResourceResolver> {
            every { resolve(any()) } returns CompletableFuture.completedFuture(testPath)
        }

        mockProvider = mockk<RemoteResourceResolverProvider> {
            every { get() } returns mockResolver
        }

        val providerField = NotificationPollingService::class.java
            .getDeclaredField("resourceResolver")
        providerField.isAccessible = true
        providerField.set(sut, mockProvider)

        // Create mock observers
        observer = mockk<() -> Unit>()
        every { observer.invoke() } just Runs

        val observersField = NotificationPollingService::class.java
            .getDeclaredField("observers")
            .apply { isAccessible = true }

        observersField.set(sut, mutableListOf(observer))
    }

    @AfterEach
    fun tearDown() {
        sut.dispose()
    }

    @Test
    fun `test pollForNotifications when ETag matches - no new notifications`() {
        every { mockResolver.checkForUpdates(any(), any()) } returns UpdateCheckResult.NoUpdates
        sut.startPolling()
        verify(exactly = 0) { observer.invoke() }
    }

    @Test
    fun `test pollForNotifications when ETag matches on startup - notify observers`() {
        every { mockResolver.checkForUpdates(any(), any()) } returns UpdateCheckResult.FirstPollCheck
        sut.startPolling()
        verify(exactly = 1) { observer.invoke() }
    }

    @Test
    fun `test pollForNotifications when ETag different - notify observers`() {
        every { mockResolver.checkForUpdates(any(), any()) } returns UpdateCheckResult.HasUpdates
        sut.startPolling()
        verify(exactly = 1) { observer.invoke() }
    }
}
