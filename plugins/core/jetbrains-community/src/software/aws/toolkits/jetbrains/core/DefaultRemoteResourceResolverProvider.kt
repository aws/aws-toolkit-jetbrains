// Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.core

import com.intellij.openapi.application.PathManager
import com.intellij.util.io.createDirectories
import software.aws.toolkits.core.utils.DefaultRemoteResourceResolver
import software.aws.toolkits.core.utils.UrlFetcher
import software.aws.toolkits.jetbrains.utils.pluginAwareExecuteOnPooledThread
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.CompletableFuture

typealias RemoteResourceResolverProvider = migration.software.aws.toolkits.jetbrains.core.RemoteResourceResolverProvider

class DefaultRemoteResourceResolverProvider : RemoteResourceResolverProvider {
    override fun get() = RESOLVER_INSTANCE

    companion object {
        private val RESOLVER_INSTANCE by lazy {
            val cachePath = Paths.get(PathManager.getSystemPath(), "aws-static-resources").createDirectories()

            DefaultRemoteResourceResolver(HttpRequestUrlFetcher, cachePath) {
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
