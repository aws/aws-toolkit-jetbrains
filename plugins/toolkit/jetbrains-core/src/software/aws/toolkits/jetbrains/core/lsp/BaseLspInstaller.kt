// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.core.lsp

import software.aws.toolkit.core.utils.debug
import software.aws.toolkit.core.utils.error
import software.aws.toolkit.core.utils.getLogger
import software.aws.toolkit.core.utils.info
import software.aws.toolkit.core.utils.warn
import java.nio.file.Path
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicReference

data class LspInstallerConfig(
    val name: String,
    val supportedVersionRange: SemVerRange,
    val manifestUrl: String,
    val serverFilename: String,
    val requiredFiles: List<String> = emptyList(),
    val storageDir: Path = getAwsCacheRoot().resolve("language-servers").resolve(name),
    /**
     * Exact root of a pre-built local server bundle. When set, [BaseLspInstaller.getServerPath]
     * resolves [serverFilename] directly under this root and bypasses the managed install entirely.
     */
    val localBundleRoot: Path? = null,
)

data class LspRelease(val version: String, val contents: List<LspContentEntry>)

abstract class BaseLspInstaller(
    protected val config: LspInstallerConfig,
    private val manifestAdapter: ManifestAdapter = DefaultManifestAdapter(),
    private val httpGetText: (String) -> String = { fetchText(it) },
    private val httpGetBytes: (String) -> ByteArray = { fetchBytes(it) },
    private val resolvePlatform: () -> String = { PlatformResolver.resolvePlatform().value },
    private val resolveArch: () -> String = { PlatformResolver.resolveArchitecture().value },
    private val sleep: (Long) -> Unit = Thread::sleep,
    private val removeDirectory: (Path) -> Unit = { LspFileUtils.deleteRecursively(it) },
) {
    protected val versionRange = config.supportedVersionRange
    protected val storageDir = config.storageDir
    protected val resolvedVersionDir = AtomicReference<Path?>(null)

    protected open fun postInstall(versionDir: Path) {}

    fun getServerPath(): Path {
        config.localBundleRoot?.let { root ->
            val serverFile = root.resolve(config.serverFilename)
            check(LspFileUtils.exists(serverFile)) {
                "Local ${config.name} bundle at $root is missing server file '${config.serverFilename}'"
            }
            LOG.info { "Using local ${config.name} bundle: $serverFile" }
            return serverFile
        }

        var manifestError: Exception? = null
        val freshManifest = try {
            val manifestJson = fetchManifestWithRetries()
            val manifest = manifestAdapter.parseManifest(manifestJson)
            saveManifestCache(manifestJson)
            manifest
        } catch (e: Exception) {
            manifestError = e
            LOG.warn(e) { "Failed to fetch/parse ${config.name} manifest, trying cached manifest" }
            null
        }

        val release = if (freshManifest != null) {
            resolveRelease(freshManifest)
        } else {
            tryFromCachedManifest() ?: run {
                LOG.warn { "No cached manifest for ${config.name}, searching installed servers" }
                return findCachedServer() ?: throw LspInstallException(
                    "Failed to fetch manifest and no cached server available for ${config.name}",
                    LspInstallException.ErrorCode.MANIFEST_FETCH_FAILED,
                    manifestError
                )
            }
        }

        val versionDir = storageDir.resolve(release.version)
        reuseInstalledServer(versionDir)?.let { serverFile ->
            LOG.info { "Reusing complete ${config.name} ${release.version} installation" }
            return serverFile
        }

        return try {
            downloadAndInstall(release)
        } catch (e: LspInstallException) {
            if (e.errorCode != LspInstallException.ErrorCode.DOWNLOAD_FAILED &&
                e.errorCode != LspInstallException.ErrorCode.EXTRACTION_FAILED
            ) {
                throw e
            }
            LOG.warn(e) { "Failed to install ${config.name} ${release.version}, searching installed servers" }
            findCachedServer() ?: throw e
        }
    }

    fun cleanupAfterResolve() {
        val version = resolvedVersionDir.get()?.fileName?.toString() ?: return
        cleanupOldVersions(version)
    }

    fun invalidateResolvedInstallation() {
        val dir = resolvedVersionDir.getAndSet(null) ?: return
        try {
            if (LspFileUtils.exists(dir)) {
                LOG.info { "Invalidating failed ${config.name} installation: ${dir.fileName}" }
                LspFileUtils.deleteRecursively(dir)
            }
        } catch (e: Exception) {
            LOG.warn(e) { "Failed to delete invalidated ${config.name} directory: $dir" }
        }
    }

    private fun markResolved(versionDir: Path) {
        postInstall(versionDir)
        if (config.requiredFiles.isNotEmpty()) {
            validateRequiredFiles(versionDir)
        }
        resolvedVersionDir.set(versionDir)
    }

    private fun reuseInstalledServer(versionDir: Path): Path? {
        if (!validateAllRequiredFiles(versionDir)) return null
        val serverFile = findServerFileInDir(versionDir) ?: return null
        markResolved(versionDir)
        return serverFile
    }

    private fun tryFromCachedManifest(): LspRelease? {
        val cached = loadManifestCache() ?: return null
        return try {
            LOG.debug { "Using cached manifest for ${config.name} offline mode" }
            val manifest = manifestAdapter.parseManifest(cached)
            resolveRelease(manifest)
        } catch (e: Exception) {
            LOG.warn(e) { "Failed to parse cached manifest for ${config.name}" }
            null
        }
    }

    internal fun resolveRelease(manifest: LspManifest): LspRelease {
        val platform = resolvePlatform()
        val arch = resolveArch()

        val compatible = manifest.versions
            .filter { !it.isDelisted }
            .mapNotNull { v -> SemVerParser.parse(v.serverVersion)?.let { v to it } }
            .filter { (_, semver) -> versionRange.satisfiedBy(semver) }

        if (compatible.isEmpty()) {
            throw LspInstallException(
                "No compatible version found for ${config.name} in range ${config.supportedVersionRange}",
                LspInstallException.ErrorCode.NO_COMPATIBLE_VERSION
            )
        }

        val withTarget = compatible.mapNotNull { (version, semver) ->
            val target = version.targets.firstOrNull { it.platform == platform && it.arch == arch }
            if (target != null) Triple(version, semver, target) else null
        }

        if (withTarget.isEmpty()) {
            throw LspInstallException(
                "No target found for $platform-$arch in any compatible version of ${config.name}",
                LspInstallException.ErrorCode.NO_COMPATIBLE_VERSION
            )
        }

        val selected = withTarget.maxByOrNull { (_, semver, _) -> semver }
            ?: error("Unreachable: withTarget list is non-empty")

        val (version, _, target) = selected
        LOG.info { "Selected ${version.serverVersion} for $platform-$arch" }

        return LspRelease(
            version = version.serverVersion,
            contents = target.contents,
        )
    }

    internal fun findCachedServer(): Path? =
        LspFileUtils.listSubdirectories(storageDir).asSequence()
            .mapNotNull { dir ->
                val ver = SemVerParser.parse(dir.fileName.toString())
                if (ver != null && validateAllRequiredFiles(dir)) {
                    val serverFile = findServerFileInDir(dir)
                    if (serverFile != null) Triple(dir, serverFile, ver) else null
                } else {
                    null
                }
            }
            .filter { (_, _, ver) -> versionRange.satisfiedBy(ver) }
            .maxByOrNull { (_, _, ver) -> ver }
            ?.let { (dir, serverFile, _) ->
                markResolved(dir)
                LOG.info { "Using fallback cached ${config.name} server: $serverFile" }
                serverFile
            }

    internal fun validateAllRequiredFiles(versionDir: Path): Boolean {
        val version = SemVerParser.parse(versionDir.fileName?.toString().orEmpty()) ?: return false
        if (!versionRange.satisfiedBy(version)) return false

        return LspFileUtils.hasRequiredFiles(versionDir, config.serverFilename, config.requiredFiles)
    }

    internal fun findServerFileInDir(versionDir: Path): Path? =
        LspFileUtils.findServerFile(versionDir, config.serverFilename)

    internal fun downloadAndInstall(release: LspRelease): Path {
        val versionDir = storageDir.resolve(release.version)
        return try {
            installRelease(release, versionDir)
        } catch (e: Exception) {
            removeFailedInstall(versionDir, e)
            throw e
        }
    }

    private fun installRelease(release: LspRelease, versionDir: Path): Path {
        LOG.info { "Downloading ${config.name} ${release.version} (${release.contents.size} content entries)" }

        val downloadedContents = release.contents.map { content ->
            val bytes = try {
                downloadWithRetries(content.url)
            } catch (e: Exception) {
                LOG.error(e) { "Failed to download ${config.name} content: ${content.filename}" }
                throw LspInstallException(
                    "Failed to download ${config.name} content: ${content.filename}",
                    LspInstallException.ErrorCode.DOWNLOAD_FAILED,
                    e
                )
            }

            verifyDownloadedSize(content, bytes)
            verifyHash(bytes, content.hashes, content.filename)
            content to bytes
        }

        preflightContents(versionDir, downloadedContents)

        return try {
            LspFileUtils.writeContents(versionDir, downloadedContents)
            validateRequiredFiles(versionDir)
            postInstall(versionDir)
            val serverPath = validateRequiredFiles(versionDir)

            resolvedVersionDir.set(versionDir)
            LOG.info { "${config.name} installed to: $serverPath" }
            serverPath
        } catch (e: LspInstallException) {
            throw e
        } catch (e: Exception) {
            LOG.error(e) { "Failed to extract ${config.name}" }
            throw LspInstallException(
                "Failed to extract ${config.name}",
                LspInstallException.ErrorCode.EXTRACTION_FAILED,
                e
            )
        }
    }

    private fun removeFailedInstall(versionDir: Path, cause: Throwable) {
        // Drop the resolved pointer only when it still references this now-removed directory, so a
        // repeated or direct install failure cannot leave resolved state aimed at a deleted install
        // while a resolution to a different valid version is left intact. AtomicReference CAS is
        // identity-based, so match on Path value first, then CAS the exact reference we observed.
        val resolved = resolvedVersionDir.get()
        if (resolved == versionDir) {
            resolvedVersionDir.compareAndSet(resolved, null)
        }
        try {
            removeDirectory(versionDir)
        } catch (cleanupError: Exception) {
            LOG.warn(cleanupError) { "Failed to remove ${config.name} directory after a failed install: $versionDir" }
            cause.addSuppressed(cleanupError)
        }
    }

    private fun preflightContents(versionDir: Path, downloadedContents: List<Pair<LspContentEntry, ByteArray>>) {
        try {
            LspFileUtils.preflightContents(versionDir, downloadedContents)
        } catch (e: LspInstallException) {
            throw e
        } catch (e: Exception) {
            LOG.error(e) { "Failed to preflight ${config.name} content" }
            throw LspInstallException(
                "Failed to extract ${config.name}",
                LspInstallException.ErrorCode.EXTRACTION_FAILED,
                e
            )
        }
    }

    private fun verifyDownloadedSize(content: LspContentEntry, bytes: ByteArray) {
        val expected = content.bytes
        if (expected <= 0) return

        val actual = bytes.size.toLong()
        if (actual != expected) {
            throw LspInstallException(
                "Downloaded size mismatch for ${config.name}/${content.filename}: expected $expected bytes, got $actual",
                LspInstallException.ErrorCode.DOWNLOAD_FAILED
            )
        }
    }

    private fun validateRequiredFiles(versionDir: Path): Path =
        LspFileUtils.requireServerAndRequiredFiles(versionDir, config.serverFilename, config.requiredFiles)

    private fun verifyHash(data: ByteArray, expectedHashes: List<String>, filename: String) {
        if (expectedHashes.isEmpty()) return

        val parseableHashes = expectedHashes.mapNotNull { parseHashString(it) }
        if (parseableHashes.isEmpty()) {
            throw LspInstallException(
                "Hash verification failed for ${config.name}/$filename: no parseable hashes in manifest",
                LspInstallException.ErrorCode.HASH_VERIFICATION_FAILED
            )
        }

        val matched = parseableHashes.any { (algorithm, hash) ->
            try {
                computeHash(data, algorithm).equals(hash, ignoreCase = true)
            } catch (e: Exception) {
                LOG.warn { "Unsupported hash algorithm '$algorithm': ${e.message}" }
                false
            }
        }

        if (!matched) {
            throw LspInstallException(
                "Hash verification failed for ${config.name}/$filename",
                LspInstallException.ErrorCode.HASH_VERIFICATION_FAILED
            )
        }
        LOG.debug { "Hash verification passed for ${config.name}/$filename" }
    }

    internal fun cleanupOldVersions(currentVersion: String) {
        try {
            val dirs = LspFileUtils.listSubdirectories(storageDir)

            val fallbackDir = dirs
                .filter { it.fileName.toString() != currentVersion }
                .filter { validateAllRequiredFiles(it) }
                .mapNotNull { dir -> SemVerParser.parse(dir.fileName.toString())?.let { dir to it } }
                .filter { (_, ver) -> versionRange.satisfiedBy(ver) }
                .maxByOrNull { (_, ver) -> ver }
                ?.first

            val keep = setOfNotNull(currentVersion, fallbackDir?.fileName?.toString())

            dirs.forEach { dir ->
                if (dir.fileName.toString() in keep) return@forEach
                LOG.debug { "Removing old ${config.name} version: ${dir.fileName}" }
                LspFileUtils.deleteRecursively(dir)
            }
        } catch (e: Exception) {
            LOG.warn(e) { "Failed to cleanup old ${config.name} versions" }
        }
    }

    private fun fetchManifestWithRetries(): String = withRetries("${config.name} manifest fetch") {
        httpGetText(config.manifestUrl)
    }

    private fun downloadWithRetries(url: String): ByteArray = withRetries("${config.name} download") {
        httpGetBytes(url)
    }

    private fun saveManifestCache(json: String) {
        try {
            LspFileUtils.writeManifestCacheAtomically(storageDir, MANIFEST_CACHE_FILE, json)
        } catch (e: Exception) {
            LOG.warn(e) { "Failed to update manifest cache for ${config.name}" }
        }
    }

    private fun loadManifestCache(): String? = try {
        LspFileUtils.readManifestCache(storageDir.resolve(MANIFEST_CACHE_FILE))
    } catch (e: Exception) {
        LOG.debug { "Failed to load manifest cache for ${config.name}: ${e.message}" }
        null
    }

    internal fun <T> withRetries(description: String, fn: () -> T): T {
        var delayMs = INITIAL_DELAY_MS
        var lastError: Exception? = null

        repeat(MAX_ATTEMPTS) { attempt ->
            try {
                return fn()
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                throw e
            } catch (e: Exception) {
                lastError = e
                if (attempt < MAX_ATTEMPTS - 1) {
                    LOG.warn(e) { "$description failed (attempt ${attempt + 1}/$MAX_ATTEMPTS), retrying" }
                    try {
                        sleep(delayMs)
                    } catch (interrupt: InterruptedException) {
                        Thread.currentThread().interrupt()
                        throw interrupt
                    }
                    delayMs *= 2
                }
            }
        }

        throw lastError ?: IllegalStateException("$description failed")
    }

    companion object {
        private val LOG = getLogger<BaseLspInstaller>()
        private const val MANIFEST_CACHE_FILE = "manifest.json"
        internal const val MAX_ATTEMPTS = 3
        internal const val INITIAL_DELAY_MS = 500L

        internal fun parseHashString(hashString: String): Pair<String, String>? {
            val parts = hashString.split(":", limit = 2)
            return if (parts.size == 2 && parts[0].isNotEmpty() && parts[1].isNotEmpty()) {
                parts[0] to parts[1]
            } else {
                null
            }
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
