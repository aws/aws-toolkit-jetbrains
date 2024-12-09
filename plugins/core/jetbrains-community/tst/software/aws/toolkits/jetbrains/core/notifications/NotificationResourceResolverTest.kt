// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.core.notifications

import com.intellij.testFramework.ApplicationExtension
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.runs
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.io.TempDir
import software.aws.toolkits.core.utils.DefaultRemoteResourceResolver
import software.aws.toolkits.core.utils.UpdateCheckResult
import software.aws.toolkits.core.utils.UrlFetcher
import java.net.HttpURLConnection
import java.net.URL
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
        val mockConnection = mockk<HttpURLConnection> {
            every { requestMethod = any() } just runs
            every { setRequestProperty(any(), any()) } just runs
            every { connect() } just runs
            every { getHeaderField("ETag") } returns expectedETag
            every { disconnect() } just runs
        }

        mockkConstructor(URL::class)
        every { anyConstructed<URL>().openConnection() } returns mockConnection

        val result = sut.checkForUpdates("http://notification.test", NotificationEtagState.getInstance())
        assertThat(result).isEqualTo(UpdateCheckResult.FirstPollCheck)

        // Second call should not return FirstPollCheck
        val secondResult = sut.checkForUpdates("http://notification.test", NotificationEtagState.getInstance())
        assertThat(secondResult).isEqualTo(UpdateCheckResult.NoUpdates)
    }

    @Test
    fun `ETag changes returns HasUpdates`() {
        NotificationEtagState.getInstance().etag = "old-etag"
        val expectedETag = "new-etag"
        val mockConnection = mockk<HttpURLConnection> {
            every { requestMethod = any() } just runs
            every { setRequestProperty(any(), any()) } just runs
            every { connect() } just runs
            every { getHeaderField("ETag") } returns expectedETag
            every { disconnect() } just runs
        }

        mockkConstructor(URL::class)
        every { anyConstructed<URL>().openConnection() } returns mockConnection

        val result = sut.checkForUpdates("http://notification.test", NotificationEtagState.getInstance())
        assertThat(result).isEqualTo(UpdateCheckResult.HasUpdates)
    }

    @Test
    fun `no ETag changes returns NoUpdates after first poll`() {
        NotificationEtagState.getInstance().etag = "same-etag"
        val expectedETag = "same-etag"
        val mockConnection = mockk<HttpURLConnection> {
            every { requestMethod = any() } just runs
            every { setRequestProperty(any(), any()) } just runs
            every { connect() } just runs
            every { getHeaderField("ETag") } returns expectedETag
            every { disconnect() } just runs
        }

        mockkConstructor(URL::class)
        every { anyConstructed<URL>().openConnection() } returns mockConnection
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
