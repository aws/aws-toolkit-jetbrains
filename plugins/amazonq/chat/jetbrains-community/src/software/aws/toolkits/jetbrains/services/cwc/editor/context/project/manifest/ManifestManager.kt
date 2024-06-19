// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cwc.editor.context.project.manifest

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.intellij.openapi.application.PathManager
import com.intellij.util.io.HttpRequests
import org.apache.commons.lang3.SystemUtils
import software.aws.toolkits.core.utils.getLogger
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.io.path.createDirectories

class ManifestManager {
//    val cachePath = Paths.get(PathManager.getDefaultSystemPathFor("LOCALAPPDATA")).resolve("project/manifest").createDirectories()
    val cloudFrontUrl = "https://aws-toolkit-language-servers.amazonaws.com/temp/manifest.json"
    val SERVER_VERSION = "0.0.1"
    private val os = getOs()
    private val arch = System.getProperty("os.arch")


    data class TargetContent (
        @JsonIgnoreProperties(ignoreUnknown = true)
        @JsonProperty("filename")
        val filename: String ?= null,
        @JsonProperty("url")
        val url: String ?= null,
        @JsonProperty("hashes")
        val contents: List<String> ?= emptyList(),
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
//        connection.setRequestProperty("If-None-Match", etag)
//        val headerFields = connection.headerFields
//        val newTag = headerFields["ETag"]?.firstOrNull()
//        if( newTag != null && etag != newTag) {
            return fetchFromRemoteAndSave()
//        } else {
//            return fetchFromCache()
//        }
    }

    private fun readManifestFile(content: String) : Manifest? {
        val mapper = ObjectMapper()
        try {
            val parsedResponse = mapper.readValue<Manifest>(content)
            return parsedResponse
            logger.info(parsedResponse.toString())
        } catch (e: Exception) {
            return null
            logger.info("error parsing manifest file ${e.message}")
        }
    }

    private fun getNodeUrlFromTarget (target: VersionTarget) :String? {
        val content = target.contents?.find{content -> content?.filename?.contains("node") == true }
        return content?.url
    }

    private fun getZipUrlFromTarget (target: VersionTarget) :String? {
        val content = target.contents?.find{content -> content?.filename?.contains("qserver") == true }
        return content?.url
    }

    private fun getTargetFromManifest(manifest: Manifest): VersionTarget? {
        val targets = manifest.versions?.find{version -> version.serverVersion != null && (version.serverVersion.contains(SERVER_VERSION))}?.targets
        if (targets.isNullOrEmpty()) {
            return null
        }
        val targetArch = if(os == "darwin" && arch.contains("arm")) "arm64" else "x64"
        return targets!!.find{ target -> target.platform == os && target.arch == targetArch }
    }

    fun getNodeUrlFromManifest(manifest: Manifest): String? {
        val target = getTargetFromManifest(manifest) ?: return null
        return getNodeUrlFromTarget(target)
    }

    fun getZipUrlFromManifest(manifest: Manifest): String? {
        val target = getTargetFromManifest(manifest) ?: return null
        return getZipUrlFromTarget(target)
    }

//    private fun fetchFromCache() : Manifest? {
//        val filePath = Paths.get(cachePath.toString(), "manifest.json")
//        try {
//            val content = Files.readString(filePath)
//            return readManifestFile(content)
//        } catch (e: IOException) {
//            logger.info("error reading manifest from cache")
//            return fetchFromRemoteAndSave()
//        }
//    }

    private fun fetchFromRemoteAndSave(): Manifest? {
        try {
            val response= HttpRequests.request(cloudFrontUrl).readString()
//            val response= HttpRequests.request(cloudFrontUrl).readBytes(null)
//            val filePath = Paths.get(cachePath.toString(), "manifest.json")
//            Files.createDirectories(filePath.parent)
//            Files.write(filePath, response)
//            val content = Files.readString(filePath)
            return readManifestFile(response)
        } catch(e: Exception) {
            return null
            logger.info("failed to save manifest from remote: ${e.message}")
        }
    }

    private fun getOs() : String {
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
