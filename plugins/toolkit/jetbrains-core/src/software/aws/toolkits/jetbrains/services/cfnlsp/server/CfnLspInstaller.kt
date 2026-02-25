// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cfnlsp.server

import com.intellij.ide.util.PropertiesComponent
import software.aws.toolkit.core.utils.debug
import software.aws.toolkit.core.utils.error
import software.aws.toolkit.core.utils.getLogger
import software.aws.toolkit.core.utils.info
import software.aws.toolkit.core.utils.warn
import software.aws.toolkits.jetbrains.core.lsp.getToolkitsCacheRoot
import software.aws.toolkits.jetbrains.utils.ZipDecompressor
import software.aws.toolkits.resources.AwsToolkitBundle.message
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import kotlin.io.path.isDirectory

internal class CfnLspInstaller(
    private val storageDir: Path = defaultStorageDir(),
    private val manifestAdapter: GitHubManifestAdapter = GitHubManifestAdapter(CfnLspEnvironment.PROD),
) {
    private val httpClient = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.NORMAL)
        .connectTimeout(java.time.Duration.ofSeconds(30))
        .build()

    private val versionRange = SemVerRange.parse(CfnLspServerConfig.SUPPORTED_VERSION_RANGE)

    fun getServerPath(): Path {
        val release = try {
            manifestAdapter.getLatestRelease().also { saveManifestCache() }
        } catch (e: Exception) {
            LOG.warn(e) { "Failed to fetch manifest, trying cached manifest" }
            tryFromCachedManifest() ?: run {
                LOG.warn { "No cached manifest, trying cached server" }
                return findCachedServer() ?: throw CfnLspException(
                    message("cloudformation.lsp.error.manifest_failed"),
                    CfnLspException.ErrorCode.MANIFEST_FETCH_FAILED,
                    e
                )
            }
        }

        val versionDir = storageDir.resolve(release.version)
        val serverPath = versionDir.resolve(CfnLspServerConfig.SERVER_FILE)

        return if (Files.exists(serverPath)) {
            LOG.info { "Using cached CloudFormation LSP ${release.version}" }
            serverPath
        } else {
            downloadAndInstall(release).also { cleanupOldVersions(release.version) }
        }
    }

    private fun tryFromCachedManifest(): ServerRelease? {
        val cached = loadManifestCache() ?: return null
        return try {
            LOG.debug { "Using cached manifest for offline mode" }
            manifestAdapter.parseManifest(cached)
        } catch (e: Exception) {
            LOG.warn(e) { "Failed to parse cached manifest" }
            null
        }
    }

    /**
     * Finds the highest compatible cached server version.
     * Uses semver comparison to pick the best available fallback.
     */
    private fun findCachedServer(): Path? {
        if (!Files.exists(storageDir)) return null

        return Files.list(storageDir).use { stream ->
            stream.toList()
                .filter { it.isDirectory() }
                .filter { Files.exists(it.resolve(CfnLspServerConfig.SERVER_FILE)) }
                .mapNotNull { dir -> SemVer.parse(dir.fileName.toString())?.let { dir to it } }
                .filter { (_, ver) -> versionRange.satisfiedBy(ver) }
                .maxByOrNull { (_, ver) -> ver }
                ?.first
                ?.resolve(CfnLspServerConfig.SERVER_FILE)
                ?.also { LOG.info { "Using fallback cached server: $it" } }
        }
    }

    private fun downloadAndInstall(release: ServerRelease): Path {
        LOG.info { "Downloading CloudFormation LSP ${release.version}" }

        val zipBytes = try {
            downloadAsset(release.downloadUrl)
        } catch (e: Exception) {
            LOG.error(e) { "Failed to download CloudFormation LSP" }
            throw CfnLspException(
                message("cloudformation.lsp.error.download_failed"),
                CfnLspException.ErrorCode.DOWNLOAD_FAILED,
                e
            )
        }

        // Verify hash if available
        if (release.hashes.isNotEmpty()) {
            verifyHash(zipBytes, release.hashes)
        }

        val targetDir = storageDir.resolve(release.version)
        try {
            Files.createDirectories(targetDir)
            ZipDecompressor(zipBytes).use { it.extract(targetDir.toFile()) }
        } catch (e: Exception) {
            LOG.error(e) { "Failed to extract CloudFormation LSP" }
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

    private fun verifyHash(data: ByteArray, expectedHashes: List<String>) {
        for (expected in expectedHashes) {
            val (algorithm, hash) = parseHashString(expected) ?: continue
            val computed = computeHash(data, algorithm)
            if (computed.equals(hash, ignoreCase = true)) {
                LOG.debug { "Hash verification passed ($algorithm)" }
                return
            }
            LOG.warn { "Hash mismatch for $algorithm: expected $hash, got $computed" }
        }
        if (expectedHashes.isNotEmpty()) {
            throw CfnLspException(
                message("cloudformation.lsp.error.hash_mismatch"),
                CfnLspException.ErrorCode.HASH_VERIFICATION_FAILED
            )
        }
    }

    /**
     * Removes old versions, keeping the current version and one compatible fallback.
     */
    private fun cleanupOldVersions(currentVersion: String) {
        if (!Files.exists(storageDir)) return

        try {
            val dirs = Files.list(storageDir).use { stream ->
                stream.filter { it.isDirectory() }.toList()
            }

            // Keep the highest compatible version other than current as fallback
            val fallbackDir = dirs
                .filter { it.fileName.toString() != currentVersion }
                .mapNotNull { dir -> SemVer.parse(dir.fileName.toString())?.let { dir to it } }
                .filter { (_, ver) -> versionRange.satisfiedBy(ver) }
                .maxByOrNull { (_, ver) -> ver }
                ?.first

            val keep = setOfNotNull(currentVersion, fallbackDir?.fileName?.toString())

            dirs.filter { it.fileName.toString() !in keep }
                .forEach { oldDir ->
                    LOG.debug { "Removing old LSP version: ${oldDir.fileName}" }
                    oldDir.toFile().deleteRecursively()
                }
        } catch (e: Exception) {
            LOG.warn(e) { "Failed to cleanup old LSP versions" }
        }
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

    private fun saveManifestCache() {
        val json = manifestAdapter.getCachedManifest() ?: return
        try {
            PropertiesComponent.getInstance().setValue(MANIFEST_CACHE_KEY, json)
        } catch (e: Exception) {
            LOG.debug { "Failed to save manifest cache: ${e.message}" }
        }
    }

    private fun loadManifestCache(): String? = try {
        PropertiesComponent.getInstance().getValue(MANIFEST_CACHE_KEY)
    } catch (e: Exception) {
        LOG.debug { "Failed to load manifest cache: ${e.message}" }
        null
    }

    companion object {
        private val LOG = getLogger<CfnLspInstaller>()
        private const val MANIFEST_CACHE_KEY = "aws.cloudformation.lsp.manifest"

        fun defaultStorageDir(): Path = getToolkitsCacheRoot().resolve("cloudformation-lsp")

        internal fun parseHashString(hashString: String): Pair<String, String>? {
            // Format: "sha256:abc123..." or "sha384:abc123..."
            val parts = hashString.split(":", limit = 2)
            return if (parts.size == 2) parts[0] to parts[1] else null
        }

        internal fun computeHash(data: ByteArray, algorithm: String): String {
            val digestAlgorithm = when (algorithm.lowercase()) {
                "sha256" -> "SHA-256"
                "sha384" -> "SHA-384"
                "sha512" -> "SHA-512"
                else -> algorithm.uppercase()
            }
            return MessageDigest.getInstance(digestAlgorithm)
                .digest(data)
                .joinToString("") { "%02x".format(it) }
        }
    }
}
