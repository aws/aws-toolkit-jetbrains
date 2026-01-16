// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cfnlsp.server

import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import software.aws.toolkits.core.utils.getLogger
import software.aws.toolkits.core.utils.info
import software.aws.toolkits.core.utils.warn
import software.aws.toolkits.jetbrains.core.lsp.getToolkitsCacheRoot
import software.aws.toolkits.jetbrains.utils.ZipDecompressor
import software.aws.toolkits.resources.AwsToolkitBundle.message
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

class CfnLspInstaller(private val storageDir: Path = defaultStorageDir()) {
    private val httpClient = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.NORMAL)
        .connectTimeout(java.time.Duration.ofSeconds(30))
        .build()
    private val manifestAdapter = GitHubManifestAdapter(CfnLspEnvironment.PROD)

    fun getServerPath(): Path =
        findExistingServer() ?: downloadAndInstallSync()

    fun getServerPathAsync(project: Project): CompletableFuture<Path> {
        findExistingServer()?.let {
            LOG.info { "Found existing CloudFormation LSP server: $it" }
            return CompletableFuture.completedFuture(it)
        }

        val future = CompletableFuture<Path>()

        ProgressManager.getInstance().run(object : Task.Backgroundable(
            project,
            message("cloudformation.lsp.downloading"),
            true
        ) {
            override fun run(indicator: ProgressIndicator) {
                try {
                    indicator.isIndeterminate = false
                    val path = downloadAndInstall(indicator)
                    future.complete(path)
                } catch (e: Exception) {
                    future.completeExceptionally(e)
                }
            }
        })

        return future
    }

    private fun findExistingServer(): Path? {
        if (!Files.exists(storageDir)) return null

        return Files.walk(storageDir, 2)
            .filter { it.fileName.toString() == CfnLspServerConfig.SERVER_FILE }
            .findFirst()
            .orElse(null)
            ?.also { LOG.info { "Found existing CloudFormation LSP server: $it" } }
    }

    private fun downloadAndInstallSync(): Path {
        LOG.info { "CloudFormation LSP server not found, downloading..." }
        return downloadAndInstall(null)
    }

    private fun downloadAndInstall(indicator: ProgressIndicator?): Path {
        indicator?.text = message("cloudformation.lsp.fetching_manifest")
        indicator?.fraction = 0.1

        val release = try {
            manifestAdapter.getLatestRelease()
        } catch (e: Exception) {
            LOG.warn(e) { "Failed to fetch CloudFormation LSP manifest" }
            throw CfnLspException(
                message("cloudformation.lsp.error.manifest_failed"),
                CfnLspException.ErrorCode.MANIFEST_FETCH_FAILED,
                e
            )
        }

        val asset = try {
            manifestAdapter.getAssetForPlatform(release)
        } catch (e: Exception) {
            LOG.warn(e) { "No compatible CloudFormation LSP version found" }
            throw CfnLspException(
                message("cloudformation.lsp.error.no_compatible_version"),
                CfnLspException.ErrorCode.NO_COMPATIBLE_VERSION,
                e
            )
        }

        indicator?.text = message("cloudformation.lsp.downloading_version", release.tagName)
        indicator?.fraction = 0.3

        LOG.info { "Downloading CloudFormation LSP ${release.tagName}" }

        val zipBytes = try {
            downloadAsset(asset.browserDownloadUrl, indicator)
        } catch (e: Exception) {
            LOG.warn(e) { "Failed to download CloudFormation LSP" }
            throw CfnLspException(
                message("cloudformation.lsp.error.download_failed"),
                CfnLspException.ErrorCode.DOWNLOAD_FAILED,
                e
            )
        }

        indicator?.text = message("cloudformation.lsp.extracting")
        indicator?.fraction = 0.8

        val targetDir = storageDir.resolve(release.tagName)
        try {
            Files.createDirectories(targetDir)
            ZipDecompressor(zipBytes).use { it.extract(targetDir.toFile()) }
        } catch (e: Exception) {
            LOG.warn(e) { "Failed to extract CloudFormation LSP" }
            throw CfnLspException(
                message("cloudformation.lsp.error.extraction_failed"),
                CfnLspException.ErrorCode.EXTRACTION_FAILED,
                e
            )
        }

        indicator?.fraction = 1.0

        val serverPath = targetDir.resolve(CfnLspServerConfig.SERVER_FILE)
        LOG.info { "CloudFormation LSP installed to: $serverPath" }
        return serverPath
    }

    private fun downloadAsset(url: String, indicator: ProgressIndicator?): ByteArray {
        val request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .timeout(java.time.Duration.ofMinutes(5))
            .GET()
            .build()

        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray())

        if (response.statusCode() != 200) {
            throw RuntimeException("HTTP ${response.statusCode()}")
        }

        indicator?.fraction = 0.7
        return response.body()
    }

    companion object {
        private val LOG = getLogger<CfnLspInstaller>()

        fun defaultStorageDir(): Path = getToolkitsCacheRoot().resolve("cloudformation-lsp")
    }
}
