// Copyright 2023 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.codemodernizer.model

import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.dataformat.xml.XmlMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.intellij.openapi.util.io.FileUtil.createTempDirectory
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import software.amazon.q.core.utils.error
import software.amazon.q.core.utils.exists
import software.amazon.q.core.utils.getLogger
import software.amazon.q.core.utils.warn
import software.aws.toolkits.jetbrains.services.codemodernizer.TransformationSummary
import software.aws.toolkits.jetbrains.services.codemodernizer.utils.unzipFile
import java.io.File
import java.util.UUID
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.Path
import kotlin.io.path.isDirectory
import kotlin.io.path.walk

/**
 * Represents a CodeModernizer artifact. Essentially a wrapper around the manifest file in the downloaded artifact zip.
 */
open class CodeModernizerArtifact(
    val zipPath: String,
    val manifest: CodeModernizerManifest,
    val patch: VirtualFile,
    val summary: TransformationSummary,
    val summaryMarkdownFile: File,
    val metrics: CodeModernizerMetrics?,
) : CodeTransformDownloadArtifact {

    companion object {
        private const val MAX_SUPPORTED_VERSION = 1.0
        private var tempDir = createTempDirectory("codeTransformArtifacts", null)
        private const val MANIFEST_FILE_NAME = "manifest.json"
        private const val SUMMARY_FILE_NAME = "summary.md"
        private const val METRICS_FILE_NAME = "metrics.json"
        val LOG = getLogger<CodeModernizerArtifact>()
        val MAPPER = jacksonObjectMapper()
        val XML_MAPPER = XmlMapper().registerKotlinModule().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

        /**
         * Extracts the file at [zipPath] and uses its contents to produce a [CodeModernizerArtifact].
         * If anything goes wrong during this process an exception is thrown.
         */
        fun create(zipPath: String): CodeModernizerArtifact {
            tempDir = createTempDirectory("codeTransformArtifacts-", UUID.randomUUID().toString())
            val path = Path(zipPath)
            if (path.exists()) {
                if (!unzipFile(path, tempDir.toPath())) {
                    LOG.error { "Could not unzip artifact" }
                    throw RuntimeException("Could not unzip artifact")
                }
                val manifest = loadManifest()
                if (manifest.version > MAX_SUPPORTED_VERSION) {
                    // If not supported we can still try to use it, i.e. the versions should largely be backwards compatible
                    LOG.warn { "Unsupported manifest.json version: ${manifest.version}" }
                }
                val patch = extractSinglePatch(manifest)
                val summary = extractSummary(manifest)
                val summaryMarkdownFile = getSummaryFile(manifest)
                val metrics = loadMetrics(manifest)
                return CodeModernizerArtifact(zipPath, manifest, patch, summary, summaryMarkdownFile, metrics)
            }
            throw RuntimeException("Could not find artifact")
        }

        private fun extractSummary(manifest: CodeModernizerManifest): TransformationSummary {
            val summaryFile = tempDir.toPath().resolve(manifest.summaryRoot).resolve(SUMMARY_FILE_NAME).toFile()
            if (!summaryFile.exists() || summaryFile.isDirectory) {
                throw RuntimeException("The summary in the downloaded zip had an unknown format")
            }
            return TransformationSummary(summaryFile.readText())
        }

        private fun getSummaryFile(manifest: CodeModernizerManifest) = tempDir.toPath().resolve(manifest.summaryRoot).resolve(SUMMARY_FILE_NAME).toFile()

        /**
         * Attempts to load the manifest from the zip file. Throws an exception if the manifest is not found or cannot be serialized.
         */
        private fun loadManifest(): CodeModernizerManifest {
            val manifestFile =
                tempDir.listFiles()
                    ?.firstOrNull { it.name.endsWith(MANIFEST_FILE_NAME) }
                    ?: throw RuntimeException("Could not find manifest")
            try {
                val manifest = MAPPER.readValue<CodeModernizerManifest>(manifestFile)
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

        private fun loadMetrics(manifest: CodeModernizerManifest): CodeModernizerMetrics? {
            try {
                val metricsFile =
                    tempDir.resolve(manifest.metricsRoot).listFiles()
                        ?.firstOrNull { it.name.endsWith(METRICS_FILE_NAME) }
                        ?: throw RuntimeException("Could not find metrics.json")
                return MAPPER.readValue<CodeModernizerMetrics>(metricsFile)
            } catch (exception: Exception) {
                // if metrics.json not present or parsing fails, can still show diff.patch and summary.md
                return null
            }
        }

        @OptIn(ExperimentalPathApi::class)
        private fun extractSinglePatch(manifest: CodeModernizerManifest): VirtualFile {
            val fileSystem = LocalFileSystem.getInstance()
            val patchesDir = tempDir.toPath().resolve(manifest.patchesRoot)
            if (!patchesDir.isDirectory()) {
                throw RuntimeException("Expected root for patches was not a directory.")
            }
            return patchesDir.walk()
                .map { fileSystem.refreshAndFindFileByNioFile(it) ?: throw RuntimeException("Could not find diff.patch") }
                .toList().first()
        }
    }
}
