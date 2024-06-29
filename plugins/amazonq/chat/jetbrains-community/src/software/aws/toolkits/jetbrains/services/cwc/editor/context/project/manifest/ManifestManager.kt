// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cwc.editor.context.project.manifest

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.intellij.util.io.HttpRequests
import org.apache.commons.lang3.SystemUtils
import software.aws.toolkits.core.utils.getLogger
import java.net.HttpURLConnection
import java.net.URL


class ManifestManager {
    private val cloudFrontUrl = "https://aws-toolkit-language-servers.amazonaws.com/temp/manifest.json"
    val SERVER_VERSION = "0.0.5"
    val currentOs = getOs()
    private val arch = System.getProperty("os.arch")


    data class TargetContent (
        @JsonIgnoreProperties(ignoreUnknown = true)
        @JsonProperty("filename")
        val filename: String ?= null,
        @JsonProperty("url")
        val url: String ?= null,
        @JsonProperty("hashes")
        val hashes: List<String> ?= emptyList(),
        @JsonProperty("bytes")
        val bytes: Number ?= null
    )

    data class VersionTarget (
        @JsonIgnoreProperties(ignoreUnknown = true)
        @JsonProperty("platform")
        val platform: String ?= null,
        @JsonProperty("arch")
        val arch: String ?= null,
        @JsonProperty("contents")
        val contents: List<TargetContent> ?= emptyList()
    )
    data class Version (
        @JsonIgnoreProperties(ignoreUnknown = true)
        @JsonProperty("serverVersion")
        val serverVersion: String ?= null,
        @JsonProperty("isDelisted")
        val isDelisted: Boolean ?= null,
        @JsonProperty("targets")
        val targets: List<VersionTarget> ?= emptyList()
    )

    data class Manifest (
        @JsonIgnoreProperties(ignoreUnknown = true)
        @JsonProperty("manifestSchemaVersion")
        val manifestSchemaVersion: String ?= null,
        @JsonProperty("artifactId")
        val artifactId: String ?= null,
        @JsonProperty("artifactDescription")
        val artifactDescription: String ?= null,
        @JsonProperty("isManifestDeprecated")
        val isManifestDeprecated: Boolean ?= null,
        @JsonProperty("versions")
        val versions: List<Version> ?= emptyList()
    )

    fun getManifest () : Manifest? {
        val url = URL(cloudFrontUrl)
        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.setRequestProperty("Accept", "application/json")
        connection.setRequestProperty("Content-Type", "application/json")
        return fetchFromRemoteAndSave()
    }

    private fun readManifestFile(content: String) : Manifest? {
        val mapper = ObjectMapper()
        try {
            val parsedResponse = mapper.readValue<Manifest>(content)
            return parsedResponse
        } catch (e: Exception) {
            return null
            logger.warn("error parsing manifest file for project context ${e.message}")
        }
    }


    private fun getTargetFromManifest(manifest: Manifest): VersionTarget? {
        val targets = manifest.versions?.find{version -> version.serverVersion != null && (version.serverVersion.contains(SERVER_VERSION))}?.targets
        if (targets.isNullOrEmpty()) {
            return null
        }
        val targetArch = if(currentOs != "windows" && (arch.contains("arm") || arch == "aarch64")) "arm64" else "x64"
        return targets!!.find{ target -> target.platform == currentOs && target.arch == targetArch }
    }

    fun getNodeContentFromManifest(manifest: Manifest): TargetContent? {
        val target = getTargetFromManifest(manifest) ?: return null
        return target.contents?.find{content -> content?.filename?.contains("node") == true }
    }


    fun getZipContentFromManifest(manifest: Manifest): TargetContent? {
        val target = getTargetFromManifest(manifest) ?: return null
        return target.contents?.find{content -> content?.filename?.contains("qserver") == true }
    }

    private fun fetchFromRemoteAndSave(): Manifest? {
        try {
            val response= HttpRequests.request(cloudFrontUrl).readString()
            return readManifestFile(response)
        } catch(e: Exception) {
            return null
            logger.info("failed to save manifest from remote: ${e.message}")
        }
    }

    fun getOs() : String {
        if (SystemUtils.IS_OS_WINDOWS) {
            return "windows"
        } else if (SystemUtils.IS_OS_MAC) {
            return "darwin"
        } else return "linux"
    }

    companion object {
        private val logger = getLogger<ManifestManager>()
        private val instance = ManifestManager()

        fun getInstance(): ManifestManager = instance
    }
}
