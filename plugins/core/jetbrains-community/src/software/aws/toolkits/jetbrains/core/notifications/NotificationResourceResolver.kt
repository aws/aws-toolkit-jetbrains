// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.core.notifications

import software.aws.toolkits.core.utils.DefaultRemoteResourceResolver
import software.aws.toolkits.core.utils.RemoteResource
import software.aws.toolkits.core.utils.RemoteResourceResolver
import software.aws.toolkits.jetbrains.core.saveFileFromUrl
import software.aws.toolkits.core.utils.exists
import java.nio.file.Path
import java.util.concurrent.Callable
import java.util.concurrent.CompletionStage
import software.aws.toolkits.core.utils.getLogger
import software.aws.toolkits.core.utils.info
import software.aws.toolkits.core.utils.warn
import com.intellij.util.io.HttpRequests
import com.intellij.openapi.application.PathManager
import com.intellij.util.io.createDirectories
import software.aws.toolkits.core.utils.UrlFetcher
import software.aws.toolkits.jetbrains.core.RemoteResourceResolverProvider
import software.aws.toolkits.jetbrains.utils.pluginAwareExecuteOnPooledThread
import java.nio.file.Paths
import java.util.concurrent.CompletableFuture

interface NotificationRemoteResourceResolverProvider {
    fun get(): NotificationResourceResolver

    companion object {
        fun getInstance(): NotificationRemoteResourceResolverProvider = service()
    }
}

class DefaultNotificationRemoteResourceResolverProvider {
    override fun get() = RESOLVER_INSTANCE

    companion object {
        private val RESOLVER_INSTANCE by lazy {
            val cachePath = Paths.get(PathManager.getSystemPath(), "aws-notifications").createDirectories()

            NotificationResourceResolver(
                urlFetcher = HttpRequestUrlFetcher,
                cacheBasePath = cachePath,
                executor = { callable ->
                    val future = CompletableFuture<Path>()
                    pluginAwareExecuteOnPooledThread {
                        try {
                            future.complete(callable.call())
                        } catch (e: Exception) {
                            future.completeExceptionally(e)
                        }
                    }
                    future
                }
            )
        }

        object HttpRequestUrlFetcher : UrlFetcher {
            override fun fetch(url: String, file: Path) {
                saveFileFromUrl(url, file)
            }
        }
    }
}


class NotificationResourceResolver(
    private val urlFetcher: UrlFetcher,
    private val cacheBasePath: Path,
    private val executor: (Callable<Path>) -> CompletionStage<Path>,
    private val etagState: NotificationEtagState = NotificationEtagState.getInstance()
) : RemoteResourceResolver {
    private val delegate = DefaultRemoteResourceResolver(urlFetcher, cacheBasePath, executor)

    fun getLocalResourcePath(resourceName: String): Path? {
        val expectedLocation = cacheBasePath.resolve(resourceName)
        return expectedLocation.existsOrNull()
    }

    override fun resolve(resource: RemoteResource): CompletionStage<Path> {
        return executor(Callable { internalResolve(resource) })
    }

    private fun internalResolve(resource: RemoteResource): Path {
        val expectedLocation = cacheBasePath.resolve(resource.name)
        val current = expectedLocation.existsOrNull()

        if (current != null) {
            val currentEtag = etagState.etag
            try {
                val remoteEtag = getEndpointETag()
                if (currentEtag == remoteEtag) {
                    LOG.info { "Existing file ($current) matches remote etag - using cached version" }
                    return current
                }
            } catch (e: Exception) {
                LOG.warn(e) { "Failed to check remote etag, using cached version if available" }
                return current
            }
        }

        // Use delegate for download logic
        return delegate.resolve(resource).toCompletableFuture().get()
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
