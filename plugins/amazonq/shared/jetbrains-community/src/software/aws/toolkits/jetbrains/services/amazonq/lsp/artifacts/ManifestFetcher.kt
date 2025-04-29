// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonq.lsp.artifacts

import org.jetbrains.annotations.VisibleForTesting
import software.aws.toolkits.core.utils.deleteIfExists
import software.aws.toolkits.core.utils.error
import software.aws.toolkits.core.utils.exists
import software.aws.toolkits.core.utils.getLogger
import software.aws.toolkits.core.utils.info
import software.aws.toolkits.core.utils.readText
import software.aws.toolkits.jetbrains.core.getETagFromUrl
import software.aws.toolkits.jetbrains.core.getTextFromUrl
import software.aws.toolkits.jetbrains.core.saveFileFromUrl
import software.aws.toolkits.jetbrains.services.amazonq.project.manifest.ManifestManager
import java.nio.file.Path

class ManifestFetcher(
    private val lspManifestUrl: String = DEFAULT_MANIFEST_URL,
    private val manifestManager: ManifestManager = ManifestManager(),
    private val manifestPath: Path = DEFAULT_MANIFEST_PATH,
) {
    companion object {
        private val logger = getLogger<ManifestFetcher>()

        private const val DEFAULT_MANIFEST_URL =
            "https://d3akiidp1wvqyg.cloudfront.net/qAgenticChatServer/0/manifest.json"

        private val DEFAULT_MANIFEST_PATH: Path = getToolkitsCommonCacheRoot()
            .resolve("aws")
            .resolve("toolkits")
            .resolve("language-servers")
            .resolve("jetbrains-lsp-manifest.json")
    }

    @get:VisibleForTesting
    internal val lspManifestFilePath: Path
        get() = manifestPath

    /**
     * Method which will be used to fetch latest manifest.
     * */
    fun fetch(): ManifestManager.Manifest? {
        val localManifest = fetchManifestFromLocal()
        if (localManifest != null) {
            return localManifest
        }
        return fetchManifestFromRemote()
    }

    @VisibleForTesting
    internal fun fetchManifestFromRemote(): ManifestManager.Manifest? {
        val manifest: ManifestManager.Manifest?
        try {
            val manifestString = getTextFromUrl(lspManifestUrl)
            manifest = manifestManager.readManifestFile(manifestString) ?: return null
        } catch (e: Exception) {
            logger.error(e) { "error fetching lsp manifest from remote URL ${e.message}" }
            return null
        }
        if (manifest.isManifestDeprecated == true) {
            logger.info { "Manifest is deprecated" }
            return null
        }
        updateManifestCache()
        logger.info { "Using manifest found from remote URL" }
        return manifest
    }

    private fun updateManifestCache() {
        try {
            saveFileFromUrl(lspManifestUrl, lspManifestFilePath)
        } catch (e: Exception) {
            logger.error(e) { "error occurred while saving lsp manifest to local cache ${e.message}" }
        }
    }

    @VisibleForTesting
    internal fun fetchManifestFromLocal(): ManifestManager.Manifest? {
        val localETag = getManifestETagFromLocal()
        val remoteETag = getManifestETagFromUrl()
        // If local and remote have same ETag, we can re-use the manifest file from local to fetch artifacts.
        // If remote manifest is null or system is offline, re-use localManifest
        if ((localETag != null && remoteETag != null && localETag == remoteETag) or (localETag != null && remoteETag == null)) {
            try {
                val manifestContent = lspManifestFilePath.readText()
                val manifest = manifestManager.readManifestFile(manifestContent)
                if (manifest != null) return manifest
                lspManifestFilePath.deleteIfExists() // delete manifest if it fails to de-serialize
            } catch (e: Exception) {
                logger.error(e) { "error reading lsp manifest file from local ${e.message}" }
                return null
            }
        }
        return null
    }

    private fun getManifestETagFromLocal(): String? {
        if (lspManifestFilePath.exists()) {
            return generateMD5Hash(lspManifestFilePath)
        }
        return null
    }

    private fun getManifestETagFromUrl(): String? {
        try {
            val actualETag = getETagFromUrl(lspManifestUrl)
            return actualETag.trim('"')
        } catch (e: Exception) {
            logger.error(e) { "error fetching ETag of lsp manifest from url." }
        }
        return null
    }
}
