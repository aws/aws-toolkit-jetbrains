// Copyright 2023 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.codemodernizer.model

import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.intellij.openapi.util.io.FileUtil.createTempDirectory
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import software.aws.toolkits.core.utils.error
import software.aws.toolkits.core.utils.exists
import software.aws.toolkits.core.utils.getLogger
import software.aws.toolkits.jetbrains.services.codemodernizer.utils.unzipFile
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.Path
import kotlin.io.path.isDirectory
import kotlin.io.path.walk

const val MANIFEST_PATH_IN_ZIP = "manifest.json"

/**
 * Represents a CodeModernizer artifact. Essentially a wrapper around the manifest file in the downloaded artifact zip.
 */
open class CodeTransformHilDownloadArtifact(
    val zipPath: String,
    val manifest: CodeTransformHilDownloadManifest,
    val pomFile: VirtualFile,
) {

    companion object {
        private val tempDir = createTempDirectory("q-hil-dependency-artifacts", null)
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
                val manifest = extractManifest()
                val pomFile = extractDependencyPom(manifest.pomFolderName)
                return CodeTransformHilDownloadArtifact(zipPath, manifest, pomFile)
            }
            throw RuntimeException("Could not find artifact")
        }

        /**
         * Attempts to extract the manifest from the zip file. Throws an exception if the manifest is not found or cannot be serialized.
         */
        private fun extractManifest(): CodeTransformHilDownloadManifest {
            val manifestFile = tempDir.listFiles()
                ?.firstOrNull { Path(it.name).endsWith(MANIFEST_PATH_IN_ZIP) }
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

        @OptIn(ExperimentalPathApi::class)
        private fun extractDependencyPom(pomFolderName: String): VirtualFile {
            val fileSystem = LocalFileSystem.getInstance()
            val pomDir = tempDir.toPath().resolve(pomFolderName)

            if (!pomDir.isDirectory()) {
                throw RuntimeException("Expected directory for pom was not a directory.")
            }
            return pomDir.walk()
                .map { fileSystem.refreshAndFindFileByNioFile(it) ?: throw RuntimeException("Could not find pom.xml") }
                .toList()
                .first()
        }
    }
}
