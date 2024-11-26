// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.core.notifications

import com.intellij.testFramework.ApplicationExtension
import com.intellij.util.io.HttpRequests
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.verify
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import software.aws.toolkits.core.utils.RemoteResourceResolver
import software.aws.toolkits.jetbrains.core.RemoteResourceResolverProvider
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicBoolean

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
        NotificationEtagState.getInstance().etag = "same"
        val firstPollField = NotificationPollingService::class.java
            .getDeclaredField("isFirstPoll")
            .apply { isAccessible = true }
        firstPollField.set(sut, AtomicBoolean(false))

        mockkStatic(HttpRequests::class) {
            every {
                HttpRequests.request(any<String>())
                    .userAgent(any())
                    .connect<String>(any())
            } returns "same"
            sut.startPolling()
        }
        verify(exactly = 0) { observer.invoke() }
    }

    @Test
    fun `test pollForNotifications when ETag matches on startup - notify observers`() {
        NotificationEtagState.getInstance().etag = "same"
        mockkStatic(HttpRequests::class) {
            every {
                HttpRequests.request(any<String>())
                    .userAgent(any())
                    .connect<String>(any())
            } returns "same"
            sut.startPolling()
        }
        verify(exactly = 1) { observer.invoke() }
    }

    @Test
    fun `test pollForNotifications when ETag different - notify observers`() {
        NotificationEtagState.getInstance().etag = "oldETag"
        mockkStatic(HttpRequests::class) {
            every {
                HttpRequests.request(any<String>())
                    .userAgent(any())
                    .connect<String>(any())
            } returns "newEtag"
            sut.startPolling()
        }
        verify(exactly = 1) { observer.invoke() }
    }
}
