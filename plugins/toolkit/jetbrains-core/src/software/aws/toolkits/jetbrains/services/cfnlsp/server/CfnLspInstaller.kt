// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cfnlsp.server

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

class CfnLspInstaller(private val storageDir: Path = defaultStorageDir()) {
    private val httpClient = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.NORMAL)
        .connectTimeout(java.time.Duration.ofSeconds(30))
        .build()
    private val manifestAdapter = GitHubManifestAdapter(CfnLspEnvironment.PROD)

    fun getServerPath(): Path =
        findExistingServer() ?: downloadAndInstall()

    private fun findExistingServer(): Path? {
        if (!Files.exists(storageDir)) return null

        return Files.walk(storageDir, 2)
            .filter { it.fileName.toString() == CfnLspServerConfig.SERVER_FILE }
            .findFirst()
            .orElse(null)
            ?.also { LOG.info { "Found existing CloudFormation LSP server: $it" } }
    }

    private fun downloadAndInstall(): Path {
        LOG.info { "CloudFormation LSP server not found, downloading..." }

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

        LOG.info { "Downloading CloudFormation LSP ${release.tagName}" }

        val zipBytes = try {
            downloadAsset(asset.browserDownloadUrl)
        } catch (e: Exception) {
            LOG.warn(e) { "Failed to download CloudFormation LSP" }
            throw CfnLspException(
                message("cloudformation.lsp.error.download_failed"),
                CfnLspException.ErrorCode.DOWNLOAD_FAILED,
                e
            )
        }

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

        val serverPath = targetDir.resolve(CfnLspServerConfig.SERVER_FILE)
        LOG.info { "CloudFormation LSP installed to: $serverPath" }
        return serverPath
    }

    private fun downloadAsset(url: String): ByteArray {
        val request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .timeout(java.time.Duration.ofMinutes(5))
            .GET()
            .build()

        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray())

        if (response.statusCode() != 200) {
            throw RuntimeException("HTTP ${response.statusCode()}")
        }

        return response.body()
    }

    companion object {
        private val LOG = getLogger<CfnLspInstaller>()

        fun defaultStorageDir(): Path = getToolkitsCacheRoot().resolve("cloudformation-lsp")
    }
}
