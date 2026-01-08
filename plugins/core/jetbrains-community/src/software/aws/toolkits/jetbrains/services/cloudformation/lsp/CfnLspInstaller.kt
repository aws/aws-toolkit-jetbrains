// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cloudformation.lsp

import com.intellij.openapi.diagnostic.logger
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.zip.ZipInputStream

class CfnLspInstaller(
    private val storageDir: Path
) {
    private val httpClient = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build()
    private val manifestAdapter = GitHubManifestAdapter(CfnLspEnvironment.PROD)

    fun getServerPath(): Path {
        val existingServer = findExistingServer()
        if (existingServer != null) {
            LOG.info("Found existing CloudFormation LSP server: $existingServer")
            return existingServer
        }

        LOG.info("CloudFormation LSP server not found, downloading synchronously...")
        return downloadAndInstall()
    }

    private fun findExistingServer(): Path? {
        if (!Files.exists(storageDir)) {
            return null
        }

        return Files.walk(storageDir, 2)
            .filter { it.fileName.toString() == CfnLspServerConfig.SERVER_FILE }
            .findFirst()
            .orElse(null)
    }

    private fun downloadAndInstall(): Path {
        val release = manifestAdapter.getLatestRelease()
        val asset = manifestAdapter.getAssetForPlatform(release)

        LOG.info("Downloading CloudFormation LSP ${release.tagName}")

        val zipFile = downloadAsset(asset.browserDownloadUrl)
        val extractedDir = extractZip(zipFile, release.tagName)
        val serverPath = extractedDir.resolve(CfnLspServerConfig.SERVER_FILE)

        LOG.info("CloudFormation LSP installed to: $serverPath")
        return serverPath
    }

    private fun downloadAsset(url: String): Path {
        val request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .GET()
            .build()

        val tempFile = Files.createTempFile("cfn-lsp", ".zip")
        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream())

        if (response.statusCode() != 200) {
            error("Download failed: ${response.statusCode()}")
        }

        response.body().use { input ->
            Files.copy(input, tempFile, StandardCopyOption.REPLACE_EXISTING)
        }

        return tempFile
    }

    private fun extractZip(zipFile: Path, version: String): Path {
        val targetDir = storageDir.resolve(version)
        Files.createDirectories(targetDir)

        ZipInputStream(Files.newInputStream(zipFile)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                val targetPath = targetDir.resolve(entry.name)

                if (entry.isDirectory) {
                    Files.createDirectories(targetPath)
                } else {
                    Files.createDirectories(targetPath.parent)
                    Files.copy(zip, targetPath, StandardCopyOption.REPLACE_EXISTING)
                }

                entry = zip.nextEntry
            }
        }

        Files.deleteIfExists(zipFile)
        return findExtractedServerDir(targetDir)
    }

    private fun findExtractedServerDir(targetDir: Path): Path {
        val dirs = Files.list(targetDir)
            .filter { Files.isDirectory(it) }
            .toList()

        if (dirs.size != 1) {
            error("Expected 1 directory, found ${dirs.size}")
        }

        return dirs.first()
    }

    companion object {
        private val LOG = logger<CfnLspInstaller>()
    }
}
