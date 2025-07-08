// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonq.lsp.artifacts

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.intellij.openapi.util.registry.Registry
import org.jetbrains.annotations.VisibleForTesting
import software.aws.toolkits.core.utils.deleteIfExists
import software.aws.toolkits.core.utils.error
import software.aws.toolkits.core.utils.exists
import software.aws.toolkits.core.utils.getLogger
import software.aws.toolkits.core.utils.info
import software.aws.toolkits.core.utils.readText
import software.aws.toolkits.core.utils.warn
import software.aws.toolkits.jetbrains.core.getETagFromUrl
import software.aws.toolkits.jetbrains.core.getTextFromUrl
import software.aws.toolkits.jetbrains.core.saveFileFromUrl
import java.nio.file.Path

class ManifestFetcher {
    companion object {
        private val mapper = jacksonObjectMapper().apply { configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false) }
        private val logger = getLogger<ManifestFetcher>()

        private fun getManifestEndpoint(): String {
            val endpoint = Registry.get("amazon.q.flare.endpoint").asString()
            return endpoint.ifBlank { "https://d3akiidp1wvqyg.cloudfront.net/qAgenticChatServer/0/manifest.json" }
        }

        private val DEFAULT_MANIFEST_PATH: Path = getToolkitsCommonCacheRoot()
            .resolve("aws")
            .resolve("toolkits")
            .resolve("language-servers")
            .resolve("jetbrains-lsp-manifest.json")
    }

    private val lspManifestUrl
        get() = getManifestEndpoint()
    private val manifestPath: Path = DEFAULT_MANIFEST_PATH

    @get:VisibleForTesting
    internal val lspManifestFilePath: Path
        get() = manifestPath

    /**
     * Method which will be used to fetch latest manifest.
     * */
    fun fetch(): Manifest? {
        val localManifest = fetchManifestFromLocal()
        if (localManifest != null) {
            return localManifest
        }
        return fetchManifestFromRemote()
    }

    @VisibleForTesting
    internal fun fetchManifestFromRemote(): Manifest? {
        val manifest: Manifest?
        try {
            val manifestString = getTextFromUrl(lspManifestUrl)
            manifest = readManifestFile(manifestString) ?: return null
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
    internal fun fetchManifestFromLocal(): Manifest? {
        val localETag = getManifestETagFromLocal()
        val remoteETag = getManifestETagFromUrl()
        // If local and remote have same ETag, we can re-use the manifest file from local to fetch artifacts.
        // If remote manifest is null or system is offline, re-use localManifest
        if ((localETag != null && remoteETag != null && localETag == remoteETag) or (localETag != null && remoteETag == null)) {
            try {
                val manifestContent = lspManifestFilePath.readText()
                val manifest = readManifestFile(manifestContent)
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

    fun readManifestFile(content: String): Manifest? {
        try {
            return mapper.readValue<Manifest>(content)
        } catch (e: Exception) {
            logger.warn { "error parsing manifest file for project context ${e.message}" }
            return null
        }
    }
}

data class TargetContent(
    @JsonProperty("filename")
    val filename: String? = null,
    @JsonProperty("url")
    val url: String? = null,
    @JsonProperty("hashes")
    val hashes: List<String>? = emptyList(),
    @JsonProperty("bytes")
    val bytes: Number? = null,
)

data class VersionTarget(
    @JsonProperty("platform")
    val platform: String? = null,
    @JsonProperty("arch")
    val arch: String? = null,
    @JsonProperty("contents")
    val contents: List<TargetContent>? = emptyList(),
)

data class Version(
    @JsonProperty("serverVersion")
    val serverVersion: String? = null,
    @JsonProperty("isDelisted")
    val isDelisted: Boolean? = null,
    @JsonProperty("targets")
    val targets: List<VersionTarget>? = emptyList(),
    @JsonProperty("thirdPartyLicenses")
    val thirdPartyLicenses: String? = null,
)

data class Manifest(
    @JsonProperty("manifestSchemaVersion")
    val manifestSchemaVersion: String? = null,
    @JsonProperty("artifactId")
    val artifactId: String? = null,
    @JsonProperty("artifactDescription")
    val artifactDescription: String? = null,
    @JsonProperty("isManifestDeprecated")
    val isManifestDeprecated: Boolean? = null,
    @JsonProperty("versions")
    val versions: List<Version>? = emptyList(),
)
