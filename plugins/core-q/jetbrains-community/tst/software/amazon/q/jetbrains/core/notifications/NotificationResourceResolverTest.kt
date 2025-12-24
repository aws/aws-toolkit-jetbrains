// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.amazon.q.jetbrains.core.notifications

import com.intellij.testFramework.ApplicationExtension
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.io.TempDir
import software.amazon.q.core.utils.DefaultRemoteResourceResolver
import software.amazon.q.core.utils.UpdateCheckResult
import software.amazon.q.core.utils.UrlFetcher
import java.nio.file.Path
import java.util.concurrent.Callable
import java.util.concurrent.CompletableFuture

@ExtendWith(ApplicationExtension::class)
class NotificationResourceResolverTest {
    private lateinit var urlFetcher: UrlFetcher
    private lateinit var sut: DefaultRemoteResourceResolver

    @TempDir
    lateinit var tempDir: Path

    @BeforeEach
    fun setUp() {
        urlFetcher = mockk()
        sut = DefaultRemoteResourceResolver(
            urlFetcher = urlFetcher,
            cacheBasePath = tempDir,
            executor = { callable: Callable<Path> -> CompletableFuture.completedFuture(callable.call()) }
        )
    }

    @Test
    fun `first poll with no ETag changes returns FirstPollCheck`() {
        NotificationEtagState.getInstance().etag = "same-etag"
        val expectedETag = "same-etag"
        every { urlFetcher.getETag(any()) } returns expectedETag

        val result = sut.checkForUpdates("http://notification.test", NotificationEtagState.getInstance())
        assertThat(result).isEqualTo(UpdateCheckResult.FirstPollCheck)
    }

    @Test
    fun `ETag changes returns HasUpdates`() {
        NotificationEtagState.getInstance().etag = "old-etag"
        val expectedETag = "new-etag"
        every { urlFetcher.getETag(any()) } returns expectedETag

        val result = sut.checkForUpdates("http://notification.test", NotificationEtagState.getInstance())
        assertThat(result).isEqualTo(UpdateCheckResult.HasUpdates)
    }

    @Test
    fun `no ETag changes returns NoUpdates after first poll`() {
        NotificationEtagState.getInstance().etag = "same-etag"
        val expectedETag = "same-etag"
        every { urlFetcher.getETag(any()) } returns expectedETag

        // sets isFirstPoll to false
        val firstResult = sut.checkForUpdates("http://notification.test", NotificationEtagState.getInstance())
        assertThat(firstResult).isEqualTo(UpdateCheckResult.FirstPollCheck)

        val secondResult = sut.checkForUpdates("http://notification.test", NotificationEtagState.getInstance())
        assertThat(secondResult).isEqualTo(UpdateCheckResult.NoUpdates)
    }

    @Test
    fun `getLocalResourcePath returns null for non-existent file`() {
        val result = sut.getLocalResourcePath("non-existent-file")
        assertThat(result).isNull()
    }
}
