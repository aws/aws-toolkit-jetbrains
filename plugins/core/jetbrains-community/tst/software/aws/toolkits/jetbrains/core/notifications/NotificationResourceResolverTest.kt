// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.core.notifications

import com.intellij.testFramework.ApplicationExtension
import com.intellij.util.io.HttpRequests
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.io.TempDir
import software.aws.toolkits.core.utils.UrlFetcher
import java.nio.file.Path
import java.util.concurrent.Callable
import java.util.concurrent.CompletableFuture

@ExtendWith(ApplicationExtension::class)
class NotificationResourceResolverTest {
    private lateinit var urlFetcher: UrlFetcher
    private lateinit var sut: NotificationResourceResolver

    @TempDir
    lateinit var tempDir: Path

    @BeforeEach
    fun setUp() {
        urlFetcher = mockk()
        sut = NotificationResourceResolver(
            urlFetcher = urlFetcher,
            cacheBasePath = tempDir,
            executor = { callable: Callable<Path> -> CompletableFuture.completedFuture(callable.call()) }
        )
    }

    @Test
    fun `first poll with no ETag changes returns FirstPollCheck`() {
        NotificationEtagState.getInstance().etag = "same-etag"
        mockkStatic(HttpRequests::class) {
            every {
                HttpRequests.request(any<String>())
                    .userAgent(any())
                    .connect<String>(any())
            } returns "same-etag"

            val result = sut.checkForUpdates()
            assertThat(result).isEqualTo(UpdateCheckResult.FirstPollCheck)

            // Second call should not return FirstPollCheck
            val secondResult = sut.checkForUpdates()
            assertThat(secondResult).isEqualTo(UpdateCheckResult.NoUpdates)
        }
    }

    @Test
    fun `ETag changes returns HasUpdates`() {
        NotificationEtagState.getInstance().etag = "old-etag"
        mockkStatic(HttpRequests::class) {
            every {
                HttpRequests.request(any<String>())
                    .userAgent(any())
                    .connect<String>(any())
            } returns "new-etag"

            val result = sut.checkForUpdates()
            assertThat(result).isEqualTo(UpdateCheckResult.HasUpdates)
        }
    }

    @Test
    fun `no ETag changes returns NoUpdates after first poll`() {
        NotificationEtagState.getInstance().etag = "same-etag"
        mockkStatic(HttpRequests::class) {
            every {
                HttpRequests.request(any<String>())
                    .userAgent(any())
                    .connect<String>(any())
            } returns "same-etag"

            // sets isFirstPoll to false
            val firstResult = sut.checkForUpdates()
            assertThat(firstResult).isEqualTo(UpdateCheckResult.FirstPollCheck)

            val secondResult = sut.checkForUpdates()
            assertThat(secondResult).isEqualTo(UpdateCheckResult.NoUpdates)
        }
    }

    @Test
    fun `getLocalResourcePath returns null for non-existent file`() {
        val result = sut.getLocalResourcePath("non-existent-file")
        assertThat(result).isNull()
    }
}
