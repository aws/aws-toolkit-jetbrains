// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonq.lsp

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.util.SystemInfo
import com.intellij.util.io.HttpRequests
import com.intellij.util.system.CpuArch
import com.intellij.util.text.SemVer
import org.apache.commons.codec.digest.DigestUtils
import software.amazon.awssdk.utils.UserHomeDirectoryUtils
import software.aws.toolkits.core.utils.getLogger
import software.aws.toolkits.core.utils.warn
import software.aws.toolkits.jetbrains.services.amazonq.project.manifest.ManifestManager
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

class ArtifactManager {
    private val manifestFetcher = ManifestFetcher()
    private val lspArtifactsPath: Path = Paths.get(UserHomeDirectoryUtils.userHomeDirectory(), ".aws", "amazonq", "cache")

    data class SupportedManifestVersionRange(
        val startVersion: SemVer,
        val endVersion: SemVer
    )

    private val supportedManifestVersionRanges = SupportedManifestVersionRange(
        SemVer("3.0.0", 3, 0, 0),
        SemVer("4.0.0", 4, 0, 0)
    )

    companion object {
        private val logger = getLogger<ArtifactManager>()
    }

    fun fetchArtifact() {
        val manifest = manifestFetcher.fetch() ?: return
        val validVersions = getValidVersionsFromLspManifest(manifest)
        val validTarget = getTargetFromLspManifest(validVersions)
        tryDeleteOldArtifacts(validTarget)
        downloadLspArtifacts(validTarget)
    }

    private fun downloadLspArtifacts(target: ManifestManager.VersionTarget?) {
        if (target == null || target.contents.isNullOrEmpty()) { return }

        for (content : ManifestManager.TargetContent in target.contents) {
            if (content.url == null || content.filename == null) continue
            val filePath = lspArtifactsPath.resolve(content.filename)
            if (!Files.exists(filePath)) {
                val bytes = HttpRequests.request(content.url).readBytes(null)
                if (validateHash(content.hashes?.first(), bytes)) {
                    saveFileToLocal(content.url, filePath)
                }
            }
        }
    }

    private fun tryDeleteOldArtifacts(target: ManifestManager.VersionTarget?) {
        if (target == null || target.contents.isNullOrEmpty()) { return }

        for (content : ManifestManager.TargetContent in target.contents) {
            if (content.url == null || content.filename == null) continue
            val filePath = lspArtifactsPath.resolve(content.filename)

            try {
                Files.deleteIfExists(filePath)
            } catch (e: Exception) {
                logger.warn(e) { "error deleting old lsp artifacts:" }
            }
        }
    }

    private fun validateHash(expectedHash: String?, input: ByteArray): Boolean {
        if (expectedHash == null) { return false }
        val sha384 = DigestUtils.sha384Hex(input)
        if (("sha384:$sha384") != expectedHash) {
            logger.warn { "failed validating hash for artifacts $expectedHash" }
            return false
        }
        return true
    }

    private fun saveFileToLocal(url: String, path: Path) {
        try {
            HttpRequests.request(url).saveToFile(path, null)
        } catch (e: IOException) {
            logger.warn { "error downloading from remote ${e.message}" }
            throw e
        }
    }

    private fun getTargetFromLspManifest(validVersions: List<ManifestManager.Version>): ManifestManager.VersionTarget? {
        if (validVersions.isEmpty()) return null

        val currentOS = when {
            SystemInfo.isWindows -> "windows"
            SystemInfo.isMac -> "darwin"
            else -> "linux"
        }
        val currentArchitecture = when {
            CpuArch.CURRENT == CpuArch.X86_64 -> "x64"
            else -> "arm64"
        }

        val currentTarget = validVersions.first().targets?.find { target ->
            target.platform == currentOS && target.arch == currentArchitecture
        }
        return currentTarget
    }

    private fun getValidVersionsFromLspManifest(manifest: ManifestManager.Manifest): List<ManifestManager.Version> {
        if (manifest.versions.isNullOrEmpty()) return emptyList()

        val versionsInRange = manifest.versions
            .mapNotNull { version ->
                version.serverVersion?.let { serverVersion ->
                    SemVer.parseFromText(serverVersion)?.let { semVer ->
                        if (semVer in supportedManifestVersionRanges.startVersion..supportedManifestVersionRanges.endVersion) {
                            version to semVer
                        } else null
                    }
                }
            }
            .sortedByDescending { (_, semVer) -> semVer }
            .map { (version, _) -> version }

        return when {
            versionsInRange.isEmpty() -> emptyList()
            //versionsInRange.size == 1 -> versionsInRange
            else -> versionsInRange.take(1)
        }
    }
}

class LSPManifestAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val artifactManager = ArtifactManager()
        artifactManager.fetchArtifact()
    }
}
