// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cloudformation.lsp

import software.aws.toolkits.core.utils.getLogger
import software.aws.toolkits.core.utils.info
import software.aws.toolkits.jetbrains.core.lsp.getToolkitsCacheRoot
import software.aws.toolkits.jetbrains.utils.ZipDecompressor
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path

class CfnLspInstaller(private val storageDir: Path = defaultStorageDir()) {
    private val httpClient = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build()
    private val manifestAdapter = GitHubManifestAdapter(CfnLspEnvironment.PROD)

    fun getServerPath(): Path {
        findExistingServer()?.let {
            LOG.info { "Found existing CloudFormation LSP server: $it" }
            return it
        }

        LOG.info { "CloudFormation LSP server not found, downloading..." }
        return downloadAndInstall()
    }

    private fun findExistingServer(): Path? {
        if (!Files.exists(storageDir)) return null

        return Files.walk(storageDir, 2)
            .filter { it.fileName.toString() == CfnLspServerConfig.SERVER_FILE }
            .findFirst()
            .orElse(null)
    }

    private fun downloadAndInstall(): Path {
        val release = manifestAdapter.getLatestRelease()
        val asset = manifestAdapter.getAssetForPlatform(release)

        LOG.info { "Downloading CloudFormation LSP ${release.tagName}" }

        val zipBytes = downloadAsset(asset.browserDownloadUrl)
        val targetDir = storageDir.resolve(release.tagName)
        Files.createDirectories(targetDir)

        ZipDecompressor(zipBytes).use { it.extract(targetDir.toFile()) }

        val serverPath = targetDir.resolve(CfnLspServerConfig.SERVER_FILE)
        LOG.info { "CloudFormation LSP installed to: $serverPath" }
        return serverPath
    }

    private fun downloadAsset(url: String): ByteArray {
        val request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .GET()
            .build()

        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray())
        if (response.statusCode() != 200) {
            error("Download failed: ${response.statusCode()}")
        }

        return response.body()
    }

    companion object {
        private val LOG = getLogger<CfnLspInstaller>()

        fun defaultStorageDir(): Path = getToolkitsCacheRoot().resolve("cloudformation-lsp")
    }
}
