// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cfnlsp.server

import software.aws.toolkit.core.utils.debug
import software.aws.toolkit.core.utils.error
import software.aws.toolkit.core.utils.getLogger
import software.aws.toolkit.core.utils.info
import software.aws.toolkit.core.utils.warn
import software.aws.toolkit.jetbrains.isDeveloperMode
import software.aws.toolkits.jetbrains.core.lsp.getToolkitsCacheRoot
import software.aws.toolkits.jetbrains.utils.ZipDecompressor
import software.aws.toolkits.resources.AwsToolkitBundle.message
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.io.path.isDirectory

internal class CfnLspInstaller(
    private val storageDir: Path = defaultStorageDir(),
    private val manifestAdapter: GitHubManifestAdapter = GitHubManifestAdapter(determineEnvironment()),
) {
    private val httpClient = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.NORMAL)
        .connectTimeout(java.time.Duration.ofSeconds(30))
        .build()

    private val versionRange = SemVerRange.parse(CfnLspServerConfig.SUPPORTED_VERSION_RANGE)
    val inUseTracker = InUseTracker()
    var resolvedVersionDir: Path? = null
        private set

    fun getServerPath(): Path {
        val devPath = tryDevBundlePath()
        if (devPath != null) {
            LOG.info { "Using dev LSP bundle: $devPath" }
            return devPath
        }

        val release = try {
            manifestAdapter.getLatestRelease()
        } catch (e: Exception) {
            saveManifestCache()
            LOG.warn(e) { "Failed to fetch manifest, trying cached manifest" }
            tryFromCachedManifest() ?: run {
                LOG.warn { "No cached manifest, searching for installed LSP" }
                return findCachedServer() ?: throw CfnLspException(
                    message("cloudformation.lsp.error.manifest_failed"),
                    CfnLspException.ErrorCode.MANIFEST_FETCH_FAILED,
                    e
                )
            }
        }
        saveManifestCache()

        val versionDir = storageDir.resolve(release.version)
        resolvedVersionDir = versionDir
        val serverPath = findServerFileInDir(versionDir)

        return if (serverPath != null) {
            LOG.info { "Using cached CloudFormation LSP ${release.version}" }
            serverPath
        } else {
            downloadAndInstall(release)
        }
    }

    /** Run post-resolve cleanup. Call AFTER the in-use marker for the current version has been written. */
    fun cleanupAfterResolve() {
        val version = resolvedVersionDir?.fileName?.toString() ?: return
        cleanupLegacyStorageDir()
        cleanupOldVersions(version)
    }

    private fun tryDevBundlePath(): Path? {
        if (!isDevelopmentEnvironment()) return null

        val devBundle = System.getenv("CFN_LSP_DEV_BUNDLE") ?: System.getProperty("cfn.lsp.dev.bundle")
        if (devBundle.isNullOrBlank()) return null

        val path = Path.of(devBundle)
        val serverFile = if (path.fileName?.toString() == CfnLspServerConfig.SERVER_FILE) {
            path
        } else {
            path.resolve(CfnLspServerConfig.SERVER_FILE)
        }

        if (Files.exists(serverFile)) return serverFile
        LOG.warn { "CFN_LSP_DEV_BUNDLE set but server file not found: $serverFile" }
        return null
    }

    private fun isDevelopmentEnvironment(): Boolean = try {
        isDeveloperMode()
    } catch (_: Exception) {
        false
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

    private fun findCachedServer(): Path? {
        if (!Files.exists(storageDir)) return null

        return Files.list(storageDir).use { stream ->
            stream.toList()
                .filter { it.isDirectory() && !it.fileName.toString().contains(".tmp.") }
                .mapNotNull { dir ->
                    val serverFile = findServerFileInDir(dir)
                    val ver = SemVer.parse(dir.fileName.toString())
                    if (serverFile != null && ver != null) Triple(dir, serverFile, ver) else null
                }
                .filter { (_, _, ver) -> versionRange.satisfiedBy(ver) }
                .maxByOrNull { (_, _, ver) -> ver }
                ?.let { (dir, serverFile, _) ->
                    resolvedVersionDir = dir
                    LOG.info { "Using fallback cached server: $serverFile" }
                    serverFile
                }
        }
    }

    /**
     * Finds the server file in a version directory, checking both the root
     * and subdirectories (zip extraction creates a subdirectory).
     */
    private fun findServerFileInDir(versionDir: Path): Path? {
        val direct = versionDir.resolve(CfnLspServerConfig.SERVER_FILE)
        if (Files.exists(direct)) return direct

        if (!Files.exists(versionDir)) return null
        return Files.list(versionDir).use { stream ->
            stream.filter { Files.isDirectory(it) }
                .map { it.resolve(CfnLspServerConfig.SERVER_FILE) }
                .filter { Files.exists(it) }
                .findFirst()
                .orElse(null)
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

        if (release.hashes.isNotEmpty()) {
            verifyHash(zipBytes, release.hashes)
        }

        val targetDir = storageDir.resolve(release.version)

        // Another IDE instance may have already installed this version
        val existingServer = findServerFileInDir(targetDir)
        if (existingServer != null) {
            LOG.info { "Server already exists at $existingServer, skipping extraction" }
            return existingServer
        }

        // Atomic to prevent partial installs visible to concurrent IDE instances
        val pid = ProcessHandle.current().pid()
        val tmpDir = storageDir.resolve("${release.version}.tmp.$pid")
        try {
            Files.createDirectories(tmpDir)
            ZipDecompressor(zipBytes).use { it.extract(tmpDir.toFile()) }

            try {
                Files.move(tmpDir, targetDir, StandardCopyOption.ATOMIC_MOVE)
            } catch (e: Exception) {
                LOG.debug { "Atomic move failed, checking if another process completed it: ${e.message}" }
                tmpDir.toFile().deleteRecursively()
                if (!Files.exists(targetDir)) {
                    throw CfnLspException(
                        message("cloudformation.lsp.error.extraction_failed"),
                        CfnLspException.ErrorCode.EXTRACTION_FAILED
                    )
                }
            }
        } catch (e: CfnLspException) {
            throw e
        } catch (e: Exception) {
            tmpDir.toFile().deleteRecursively()
            LOG.error(e) { "Failed to extract CloudFormation LSP" }
            throw CfnLspException(
                message("cloudformation.lsp.error.extraction_failed"),
                CfnLspException.ErrorCode.EXTRACTION_FAILED,
                e
            )
        }

        val serverPath = findServerFileInDir(targetDir)
            ?: throw CfnLspException(
                "Server file not found after extraction in $targetDir",
                CfnLspException.ErrorCode.EXTRACTION_FAILED
            )
        LOG.info { "CloudFormation LSP installed to: $serverPath" }
        return serverPath
    }

    private fun verifyHash(data: ByteArray, expectedHashes: List<String>) {
        for (expected in expectedHashes) {
            val (algorithm, hash) = HashUtils.parseHashString(expected) ?: continue
            val computed = HashUtils.computeHash(data, algorithm)
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

    private fun cleanupLegacyStorageDir() {
        val legacyDir = getToolkitsCacheRoot().resolve("cloudformation-lsp")
        if (!Files.exists(legacyDir)) return
        try {
            legacyDir.toFile().deleteRecursively()
            LOG.info { "Removed legacy LSP directory: $legacyDir" }
        } catch (e: Exception) {
            LOG.warn(e) { "Failed to remove legacy LSP directory: $legacyDir" }
        }
    }

    private fun cleanupOldVersions(currentVersion: String) {
        if (!Files.exists(storageDir)) return

        try {
            val allDirs = Files.list(storageDir).use { it.toList() }.filter { Files.isDirectory(it) }

            // Sweep stale .tmp.<pid> dirs from crashed runs
            allDirs.filter { it.fileName.toString().contains(".tmp.") }
                .forEach { tmpDir ->
                    val pid = tmpDir.fileName.toString().substringAfterLast(".tmp.").toLongOrNull()
                    if (pid == null || !ProcessHandle.of(pid).isPresent) {
                        LOG.debug { "Removing stale tmp dir: ${tmpDir.fileName}" }
                        tmpDir.toFile().deleteRecursively()
                    }
                }

            val dirs = allDirs.filter { !it.fileName.toString().contains(".tmp.") }

            val fallbackDir = dirs
                .filter { it.fileName.toString() != currentVersion }
                .mapNotNull { dir -> SemVer.parse(dir.fileName.toString())?.let { dir to it } }
                .filter { (_, ver) -> versionRange.satisfiedBy(ver) }
                .maxByOrNull { (_, ver) -> ver }
                ?.first

            val keep = setOfNotNull(currentVersion, fallbackDir?.fileName?.toString())

            dirs.filter { it.fileName.toString() !in keep }
                .forEach { oldDir ->
                    inUseTracker.cleanStaleMarkers(oldDir)
                    if (inUseTracker.isInUse(oldDir)) {
                        LOG.debug { "Skipping in-use version: ${oldDir.fileName}" }
                        return@forEach
                    }
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
            Files.createDirectories(storageDir)
            val cachePath = storageDir.resolve(MANIFEST_CACHE_FILE)
            val tmpPath = storageDir.resolve("$MANIFEST_CACHE_FILE.tmp.${ProcessHandle.current().pid()}")
            Files.writeString(tmpPath, json)
            Files.move(tmpPath, cachePath, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (e: Exception) {
            LOG.debug { "Failed to save manifest cache: ${e.message}" }
        }
    }

    private fun loadManifestCache(): String? = try {
        val cachePath = storageDir.resolve(MANIFEST_CACHE_FILE)
        if (Files.exists(cachePath)) Files.readString(cachePath) else null
    } catch (e: Exception) {
        LOG.debug { "Failed to load manifest cache: ${e.message}" }
        null
    }

    companion object {
        private val LOG = getLogger<CfnLspInstaller>()
        private const val MANIFEST_CACHE_FILE = "manifest.json"

        fun defaultStorageDir(): Path = cfnLspCacheRoot()

        private fun cfnLspCacheRoot(): Path = when {
            com.intellij.openapi.util.SystemInfo.isWindows -> java.nio.file.Paths.get(System.getenv("LOCALAPPDATA"))
            com.intellij.openapi.util.SystemInfo.isMac -> java.nio.file.Paths.get(System.getProperty("user.home"), "Library", "Caches")
            else -> java.nio.file.Paths.get(System.getProperty("user.home"), ".cache")
        }.resolve("aws").resolve("language-servers").resolve("cloudformation-languageserver")

        private fun determineEnvironment(): CfnLspEnvironment {
            val envProp = System.getProperty("cfn.lsp.environment") ?: System.getenv("CFN_LSP_ENVIRONMENT")
            if (envProp != null) {
                try {
                    return CfnLspEnvironment.valueOf(envProp.uppercase())
                } catch (_: IllegalArgumentException) {
                }
            }

            return CfnLspEnvironment.PROD
        }
    }
}
