// Copyright 2023 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.codemodernizer.model

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.intellij.openapi.util.io.FileUtil.createTempDirectory
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import software.aws.toolkits.core.utils.error
import software.aws.toolkits.core.utils.exists
import software.aws.toolkits.core.utils.getLogger
import software.aws.toolkits.core.utils.warn
import software.aws.toolkits.jetbrains.services.codemodernizer.TransformationSummary
import software.aws.toolkits.jetbrains.services.codemodernizer.utils.unzipFile
import software.aws.toolkits.jetbrains.utils.notifyStickyInfo
import java.io.File
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.Path
import kotlin.io.path.isDirectory
import kotlin.io.path.name
import kotlin.io.path.walk

/**
 * Represents a CodeModernizer artifact. Essentially a wrapper around the manifest file in the downloaded artifact zip.
 */
open class CodeModernizerArtifact(
    val zipPath: String,
    val manifest: CodeModernizerManifest,
    val patches: List<VirtualFile>,
    val description: List<PatchInfo>?,
    val summary: TransformationSummary,
    val summaryMarkdownFile: File,
) : CodeTransformDownloadArtifact {

    companion object {
        private const val maxSupportedVersion = 1.0
        private var tempDir = createTempDirectory("codeTransformArtifacts", null)
        private const val manifestPathInZip = "manifest.json"
        private const val summaryNameInZip = "summary.md"
        val LOG = getLogger<CodeModernizerArtifact>()
        private val MAPPER = jacksonObjectMapper()

        /**
         * Extracts the file at [zipPath] and uses its contents to produce a [CodeModernizerArtifact].
         * If anything goes wrong during this process an exception is thrown.
         */
        fun create(zipPath: String): CodeModernizerArtifact {
            val path = Path(zipPath)
            if (path.exists()) {
                if (!unzipFile(path, tempDir.toPath())) {
                    LOG.error { "Could not unzip artifact" }
                    throw RuntimeException("Could not unzip artifact")
                }
//                notifyStickyInfo("Zip", path.name)
//                notifyStickyInfo("Zip", tempDir.path)
                tempDir = tempDir.resolve("SampleArtifact")
                val manifest = loadManifest()
                if (manifest.version > maxSupportedVersion) {
                    // If not supported we can still try to use it, i.e. the versions should largely be backwards compatible
                    LOG.warn { "Unsupported version: ${manifest.version}" }
                }
                val patches = extractPatches(manifest)
                val description = loadDescription(manifest)
                val summary = extractSummary(manifest)
                val summaryMarkdownFile = getSummaryFile(manifest)
//                if (patches.size != 1) throw RuntimeException("Expected 1 patch, but found ${patches.size}")
                return CodeModernizerArtifact(zipPath, manifest, patches, description, summary, summaryMarkdownFile)
            }
            throw RuntimeException("Could not find artifact")
        }

        private fun extractSummary(manifest: CodeModernizerManifest): TransformationSummary {
            val summaryFile = tempDir.toPath().resolve(manifest.summaryRoot).resolve(summaryNameInZip).toFile()
            if (!summaryFile.exists() || summaryFile.isDirectory) {
                throw RuntimeException("The summary in the downloaded zip had an unknown format")
            }
            return TransformationSummary(summaryFile.readText())
        }

        private fun getSummaryFile(manifest: CodeModernizerManifest) = tempDir.toPath().resolve(manifest.summaryRoot).resolve(summaryNameInZip).toFile()

        /**
         * Attempts to load the manifest from the zip file. Throws an exception if the manifest is not found or cannot be serialized.
         */
        private fun loadManifest(): CodeModernizerManifest {
            val manifestFile =
                tempDir.listFiles()
                    ?.firstOrNull { it.name.endsWith(manifestPathInZip) }
                    ?: throw RuntimeException("Could not find manifest")
            try {
                val manifest = MAPPER.readValue(manifestFile, CodeModernizerManifest::class.java)
                if (manifest.version == 0.0F) {
                    throw RuntimeException(
                        "Unable to deserialize the manifest",
                    )
                }
                return manifest
            } catch (exception: JsonProcessingException) {
                throw RuntimeException("Unable to deserialize the manifest")
            }
        }

        @OptIn(ExperimentalPathApi::class)
        private fun extractPatches(manifest: CodeModernizerManifest): List<VirtualFile> {
            val fileSystem = LocalFileSystem.getInstance()
            val patchesDir = tempDir.toPath().resolve(manifest.patchesRoot)
            if (!patchesDir.isDirectory()) {
                throw RuntimeException("Expected root for patches was not a directory.")
            }
            return patchesDir.walk()
                .filter { it.toString().endsWith(".patch") }
                .map { fileSystem.findFileByNioFile(it) ?: throw RuntimeException("Could not find patch") }
                .sortedWith(compareBy { it.name })
                .toList()
        }

        @OptIn(ExperimentalPathApi::class)
        private fun loadDescription(manifest: CodeModernizerManifest): List<PatchInfo>? {
            val fileSystem = LocalFileSystem.getInstance()
            val patchesDir = tempDir.toPath().resolve(manifest.patchesRoot).toFile()
            if (!patchesDir.isDirectory) {
                throw RuntimeException("Expected root for patches was not a directory.")
            }

            val descriptionFile = patchesDir.listFiles()
                ?.firstOrNull { it.name.endsWith(".json") }

            if (descriptionFile == null) {
                // No JSON description file found, return null
                return null
            }

            val patchInfoList: List<PatchInfo> = MAPPER.readValue(
                descriptionFile,
                MAPPER.typeFactory.constructCollectionType(List::class.java, PatchInfo::class.java)
            )

            return patchInfoList
        }
    }
}
