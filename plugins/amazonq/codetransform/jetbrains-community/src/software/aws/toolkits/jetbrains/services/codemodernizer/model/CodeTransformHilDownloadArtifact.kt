// Copyright 2023 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.codemodernizer.model

import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.intellij.openapi.util.io.FileUtil.createTempDirectory
import software.aws.toolkits.core.utils.error
import software.aws.toolkits.core.utils.exists
import software.aws.toolkits.core.utils.getLogger
import software.aws.toolkits.jetbrains.services.codemodernizer.utils.unzipFile
import kotlin.io.path.Path

/**
 * Represents a CodeModernizer artifact. Essentially a wrapper around the manifest file in the downloaded artifact zip.
 */
open class CodeTransformHilDownloadArtifact(
    val zipPath: String,
    val manifest: CodeTransformHilDownloadManifest,
    // TODO
    //private val pom: VirtualFile,
) {

    companion object {
        private val tempDir = createTempDirectory("q-hil-dependency-artifacts", null)
        private const val manifestPathInZip = "manifest.json"

        // TODO
        //private const val summaryNameInZip = "summary.md"

        val LOG = getLogger<CodeTransformHilDownloadArtifact>()
        private val MAPPER = jacksonObjectMapper()

        /**
         * Extracts the file at [zipPath] and uses its contents to produce a [CodeTransformHilDownloadArtifact].
         * If anything goes wrong during this process an exception is thrown.
         */
        fun create(zipPath: String): CodeTransformHilDownloadArtifact {
            val path = Path(zipPath)
            if (path.exists()) {
                if (!unzipFile(path, tempDir.toPath())) {
                    LOG.error { "Could not unzip artifact" }
                    throw RuntimeException("Could not unzip artifact")
                }
                val manifest = loadManifest()
                //val patches = extractPatches(manifest)
                //val summary = extractSummary(manifest)
                //if (patches.size != 1) throw RuntimeException("Expected 1 patch, but found ${patches.size}")
                return CodeTransformHilDownloadArtifact(zipPath, manifest)
                //return CodeTransformHilArtifact(zipPath, manifest, patches, summary)
            }
            throw RuntimeException("Could not find artifact")
        }

        /**
         * Attempts to load the manifest from the zip file. Throws an exception if the manifest is not found or cannot be serialized.
         */
        private fun loadManifest(): CodeTransformHilDownloadManifest {
            val manifestFile = tempDir.listFiles()
                ?.firstOrNull { Path(it.name).endsWith(manifestPathInZip) }
                ?: throw RuntimeException("Could not find manifest")
            try {
                val manifest = MAPPER.readValue(manifestFile, CodeTransformHilDownloadManifest::class.java)
                if (
                        // TODO fix type
                        manifest.pomArtifactId == null
                        || manifest.pomFolderName == null
                        || manifest.hilCapability == null
                        || manifest.pomGroupId == null
                    ) {
                    throw RuntimeException(
                        "Unable to deserialize the manifest"
                    )
                }
                return manifest
            } catch (exception: JsonProcessingException) {
                throw RuntimeException("Unable to deserialize the manifest")
            }
        }

        /*
        private fun extractSummary(manifest: CodeModernizerManifest): TransformationSummary {
            val summaryFile = tempDir.toPath().resolve(manifest.summaryRoot).resolve(summaryNameInZip).toFile()
            if (!summaryFile.exists() || summaryFile.isDirectory) {
                throw RuntimeException("The summary in the downloaded zip had an unknown format")
            }
            return TransformationSummary(summaryFile.readText())
        }


        // TODO read on file operations
        @OptIn(ExperimentalPathApi::class)
        private fun extractPatches(manifest: CodeModernizerManifest): List<VirtualFile> {
            val fileSystem = LocalFileSystem.getInstance()
            val patchesDir = tempDir.toPath().resolve(manifest.patchesRoot)
            if (!patchesDir.isDirectory()) {
                throw RuntimeException("Expected root for patches was not a directory.")
            }
            return patchesDir.walk()
                .map { fileSystem.refreshAndFindFileByNioFile(it) ?: throw RuntimeException("Could not find patch") }
                .toList()
        }
    */
    }
}
