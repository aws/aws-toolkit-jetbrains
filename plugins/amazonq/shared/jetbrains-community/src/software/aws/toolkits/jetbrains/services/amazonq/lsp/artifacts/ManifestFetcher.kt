// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonq.lsp.artifacts

import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.io.DigestUtil
import software.aws.toolkits.core.utils.exists
import software.aws.toolkits.core.utils.getLogger
import software.aws.toolkits.core.utils.readText
import software.aws.toolkits.jetbrains.core.getETagFromUrl
import software.aws.toolkits.jetbrains.core.getTextFromUrl
import software.aws.toolkits.jetbrains.core.saveFileFromUrl
import software.aws.toolkits.jetbrains.services.amazonq.project.manifest.ManifestManager
import java.nio.file.Path

class ManifestFetcher {

    private val lspManifestUrl = "https://aws-toolkit-language-servers.amazonaws.com/codewhisperer/0/manifest.json"
    private val manifestManager = ManifestManager()
    private val lspManifestFilePath: Path = getToolkitsCommonCachePath().resolve("aws").resolve("toolkits").resolve("language-servers")
        .resolve("lsp-manifest.json")

    companion object {
        private val logger = getLogger<ManifestFetcher>()
    }

    /**
     * Method which will be used to fetch latest manifest.
     * */
    fun fetch(): ManifestManager.Manifest? {
        val localManifest = fetchManifestFromLocal()
        if (localManifest != null) {
            return localManifest
        }
        val remoteManifest = fetchManifestFromRemote() ?: return null
        return remoteManifest
    }

    private fun fetchManifestFromRemote(): ManifestManager.Manifest? {
        val manifest: ManifestManager.Manifest?
        try {
            val manifestString = getTextFromUrl(lspManifestUrl)
            manifest = manifestManager.readManifestFile(manifestString) ?: return null
        } catch (e: Exception) {
            logger.error("error fetching lsp manifest from remote URL ${e.message}", e)
            return null
        }
        if (manifest.isManifestDeprecated == true) {
            logger.info("Manifest is deprecated")
            return null
        }
        updateManifestCache()
        logger.info("Using manifest found from remote URL")
        return manifest
    }

    private fun updateManifestCache() {
        try {
            saveFileFromUrl(lspManifestUrl, lspManifestFilePath)
        } catch (e: Exception) {
            logger.error("error occurred while saving lsp manifest to local cache ${e.message}", e)
        }
    }

    private fun fetchManifestFromLocal(): ManifestManager.Manifest? {
        val localETag = getManifestETagFromLocal()
        val remoteETag = getManifestETagFromUrl()
        // If local and remote have same ETag, we can re-use the manifest file from local to fetch artifacts.
        if (localETag != null && remoteETag != null && localETag == remoteETag) {
            try {
                val manifestContent = lspManifestFilePath.readText()
                return manifestManager.readManifestFile(manifestContent)
            } catch (e: Exception) {
                logger.error("error reading lsp manifest file from local ${e.message}", e)
                return null
            }
        }
        return null
    }

    private fun getManifestETagFromLocal(): String? {
        if (lspManifestFilePath.exists()) {
            val messageDigest = DigestUtil.md5()
            DigestUtil.updateContentHash(messageDigest, lspManifestFilePath)
            return StringUtil.toHexString(messageDigest.digest())
        }
        return null
    }

    private fun getManifestETagFromUrl(): String? {
        try {
            val actualETag = getETagFromUrl(lspManifestUrl)
            return actualETag.trim('"')
        } catch (e: Exception) {
            logger.error("error fetching ETag of lsp manifest from url.", e)
        }
        return null
    }
}
