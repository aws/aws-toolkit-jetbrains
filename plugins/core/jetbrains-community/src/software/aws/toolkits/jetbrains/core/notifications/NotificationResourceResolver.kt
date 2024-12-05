// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.core.notifications

import com.intellij.openapi.application.PathManager
import com.intellij.openapi.components.service
import com.intellij.util.io.HttpRequests
import com.intellij.util.io.createDirectories
import software.aws.toolkits.core.utils.DefaultRemoteResourceResolver
import software.aws.toolkits.core.utils.RemoteResource
import software.aws.toolkits.core.utils.RemoteResourceResolver
import software.aws.toolkits.core.utils.UrlFetcher
import software.aws.toolkits.core.utils.exists
import software.aws.toolkits.core.utils.getLogger
import software.aws.toolkits.core.utils.warn
import software.aws.toolkits.jetbrains.core.saveFileFromUrl
import software.aws.toolkits.jetbrains.utils.pluginAwareExecuteOnPooledThread
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.Callable
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.concurrent.atomic.AtomicBoolean

interface NotificationResourceResolverProvider {
    fun get(): NotificationResourceResolver

    companion object {
        fun getInstance(): NotificationResourceResolverProvider = service()
    }
}

class DefaultNotificationResourceResolverProvider : NotificationResourceResolverProvider {
    override fun get() = RESOLVER_INSTANCE

    companion object {
        private val RESOLVER_INSTANCE by lazy {
            val cachePath = Paths.get(PathManager.getSystemPath(), "aws-notifications").createDirectories()

            NotificationResourceResolver(HttpRequestUrlFetcher, cachePath) {
                val future = CompletableFuture<Path>()
                pluginAwareExecuteOnPooledThread {
                    try {
                        future.complete(it.call())
                    } catch (e: Exception) {
                        future.completeExceptionally(e)
                    }
                }
                future
            }
        }

        object HttpRequestUrlFetcher : UrlFetcher {
            override fun fetch(url: String, file: Path) {
                saveFileFromUrl(url, file)
            }
        }
    }
}

sealed class UpdateCheckResult {
    object HasUpdates : UpdateCheckResult()
    object NoUpdates : UpdateCheckResult()
    object FirstPollCheck : UpdateCheckResult()
}

class NotificationResourceResolver(
    private val urlFetcher: UrlFetcher,
    private val cacheBasePath: Path,
    private val executor: (Callable<Path>) -> CompletionStage<Path>,
) : RemoteResourceResolver {
    private val delegate = DefaultRemoteResourceResolver(urlFetcher, cacheBasePath, executor)
    private val etagState: NotificationEtagState = NotificationEtagState.getInstance()
    private val isFirstPoll = AtomicBoolean(true)

    fun getLocalResourcePath(resourceName: String): Path? {
        val expectedLocation = cacheBasePath.resolve(resourceName)
        return expectedLocation.existsOrNull()
    }

    fun checkForUpdates(): UpdateCheckResult {
        val hasETagUpdate = updateETags()

        // for when we need to notify on first poll even when there's no new ETag
        if (isFirstPoll.compareAndSet(true, false) && !hasETagUpdate) {
            return UpdateCheckResult.FirstPollCheck
        }

        return if (hasETagUpdate) {
            UpdateCheckResult.HasUpdates
        } else {
            UpdateCheckResult.NoUpdates
        }
    }

    fun updateETags(): Boolean {
        val currentEtag = etagState.etag
        val remoteEtag = getEndpointETag()
        etagState.etag = remoteEtag
        return currentEtag != remoteEtag
    }

    override fun resolve(resource: RemoteResource): CompletionStage<Path> {
        return delegate.resolve(resource)
    }

    private fun getEndpointETag(): String =
        try {
            HttpRequests.request(NotificationEndpoint.getEndpoint())
                .userAgent("AWS Toolkit for JetBrains")
                .connect { request ->
                    request.connection.headerFields["ETag"]?.firstOrNull().orEmpty()
                }
        } catch (e: Exception) {
            LOG.warn { "Failed to fetch notification ETag: $e.message" }
            throw e
        }

    companion object {
        private val LOG = getLogger<NotificationResourceResolver>()
        fun Path.existsOrNull() = if (this.exists()) {
            this
        } else {
            null
        }
    }
}
