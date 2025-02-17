// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonq.lsp

import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.io.DigestUtil
import com.intellij.util.io.HttpRequests
import com.intellij.util.text.SemVer
import software.amazon.awssdk.utils.UserHomeDirectoryUtils
import software.aws.toolkits.core.utils.exists
import software.aws.toolkits.core.utils.getLogger
import software.aws.toolkits.core.utils.readText
import software.aws.toolkits.jetbrains.core.getTextFromUrl
import software.aws.toolkits.jetbrains.core.saveFileFromUrl
import software.aws.toolkits.jetbrains.services.amazonq.project.manifest.ManifestManager
import java.nio.file.Path
import java.nio.file.Paths


class ManifestFetcher {

    private val lspManifestUrl = "https://aws-toolkit-language-servers.amazonaws.com/codewhisperer/0/manifest.json"
    private val manifestManager = ManifestManager()
    private val lspManifestFilePath: Path = Paths.get(UserHomeDirectoryUtils.userHomeDirectory(), ".aws", "amazonq", "cache", "lspManifest.js")

    companion object {
        private val LOG = getLogger<ManifestFetcher>()
    }

    data class SupportedManifestVersionRange(
        val startVersion: SemVer,
        val endVersion: SemVer
    )

    private val supportedManifestVersionRanges = SupportedManifestVersionRange(
        SemVer("3.0.0", 3, 0,0),
        SemVer("4.0.0", 4, 0,0)
    )

    /**
     * Method which will be used to fetch latest manifest.
     * */
    fun fetch() : ManifestManager.Manifest? {
        val localManifest = fetchManifestFromLocal()
        if (localManifest != null) {
            return localManifest
        }
        val remoteManifest = fetchManifestFromRemote() ?: return null
        return remoteManifest
    }

    private fun fetchManifestFromRemote() : ManifestManager.Manifest? {
        val manifest : ManifestManager.Manifest?
        try {
            val manifestString = getTextFromUrl(lspManifestUrl)
            manifest = manifestManager.readManifestFile(manifestString) ?: return null
        }
        catch (e: Exception) {
            LOG.error("error fetching lsp manifest from remote URL ${e.message}", e)
            return null
        }
        if (manifest.isManifestDeprecated == true) {
            LOG.info("Manifest is deprecated")
            return null
        }
        updateManifestCache()
        LOG.info("Using manifest found from remote URL")
        return manifest
    }

    private fun updateManifestCache() {
        try {
            saveFileFromUrl(lspManifestUrl, lspManifestFilePath)
        }
        catch (e: Exception) {
            LOG.error("error occurred while saving lsp manifest to local cache ${e.message}", e)
        }
    }

    private fun fetchManifestFromLocal() : ManifestManager.Manifest? {
        var manifest : ManifestManager.Manifest? = null
        val localETag = getManifestETagFromLocal()
        val remoteETag = getManifestETagFromUrl()
        // If local and remote have same ETag, we can re-use the manifest file from local to fetch artifacts.
        if (localETag != null && remoteETag != null && localETag == remoteETag) {
            try {
                val manifestContent = lspManifestFilePath.readText()
                manifest = manifestManager.readManifestFile(manifestContent) ?: return null
            }
            catch (e: Exception) {
                LOG.error("error reading lsp manifest file from local ${e.message}", e)
                return null
            }
        }
        LOG.info("Re-using lsp manifest from local.")
        return manifest
    }

    private fun getManifestETagFromLocal() : String? {
        if(lspManifestFilePath.exists()) {
            val messageDigest = DigestUtil.md5()
            DigestUtil.updateContentHash(messageDigest, lspManifestFilePath)
            return StringUtil.toHexString(messageDigest.digest())
        }
        return null
    }

    private fun getManifestETagFromUrl() : String? {
        val actualETag = HttpRequests.head(lspManifestUrl)
            .userAgent("AWS Toolkit for JetBrains")
            .connect { request ->
                request.connection.headerFields["ETag"]?.firstOrNull().orEmpty()
            }
        return actualETag
    }
}
