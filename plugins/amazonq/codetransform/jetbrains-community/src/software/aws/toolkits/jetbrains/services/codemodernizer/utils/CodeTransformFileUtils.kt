// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.codemodernizer.utils

import com.fasterxml.jackson.dataformat.xml.JacksonXmlModule
import com.fasterxml.jackson.dataformat.xml.XmlMapper
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VirtualFile
import software.aws.toolkits.core.utils.createParentDirectories
import software.aws.toolkits.core.utils.error
import software.aws.toolkits.core.utils.exists
import software.aws.toolkits.core.utils.getLogger
import software.aws.toolkits.core.utils.info
import software.aws.toolkits.jetbrains.services.codemodernizer.CodeModernizerManager.Companion.LOG
import software.aws.toolkits.jetbrains.services.codemodernizer.constants.HIL_ARTIFACT_DIR_NAME
import software.aws.toolkits.jetbrains.services.codemodernizer.constants.HIL_ARTIFACT_POMFOLDER_DIR_NAME
import software.aws.toolkits.jetbrains.services.codemodernizer.constants.HIL_DEPENDENCY_REPORT_DIR_NAME
import software.aws.toolkits.jetbrains.services.codemodernizer.constants.HIL_DEPENDENCY_REPORT_FILE_NAME
import software.aws.toolkits.jetbrains.services.codemodernizer.constants.HIL_DEPENDENCY_ROOT_DIR_NAME
import software.aws.toolkits.jetbrains.services.codemodernizer.constants.HIL_POM_FILE_NAME
import software.aws.toolkits.jetbrains.services.codemodernizer.constants.HIL_POM_VERSION_PLACEHOLDER
import software.aws.toolkits.jetbrains.services.codemodernizer.constants.HIL_UPLOAD_ZIP_NAME
import software.aws.toolkits.jetbrains.services.codemodernizer.controller.CodeTransformChatController
import software.aws.toolkits.jetbrains.services.codemodernizer.model.DependencyUpdatesReport
import software.aws.toolkits.jetbrains.services.codemodernizer.model.MAVEN_CONFIGURATION_FILE_NAME
import software.aws.toolkits.jetbrains.services.codemodernizer.model.SqlMetadataValidationResult
import software.aws.toolkits.jetbrains.services.codewhisperer.util.content
import software.aws.toolkits.resources.message
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Path
import java.util.zip.ZipFile
import kotlin.io.path.Path

fun filterOnlyParentFiles(filePaths: Set<VirtualFile>): List<VirtualFile> {
    if (filePaths.isEmpty()) return emptyList()
    // sorts it like:
    // foo
    // foo/bar
    // foo/bar/bas
    val sorted = filePaths.sortedBy { Path(it.path).nameCount }
    val uniquePrefixes = mutableSetOf(Path(sorted.first().path).parent)
    val shortestRoots = mutableSetOf(sorted.first())
    shortestRoots.add(sorted.first())
    sorted.drop(1).forEach { file ->
        if (uniquePrefixes.none { Path(file.path).startsWith(it) }) {
            shortestRoots.add(file)
            uniquePrefixes.add(Path(file.path).parent)
        } else if (Path(file.path).parent in uniquePrefixes) {
            shortestRoots.add(file) // handles multiple parent files on the same level
        }
    }
    return shortestRoots.toList()
}

/**
 * @description For every directory, check if any supported build files (pom.xml etc) exists.
 * If we find a valid build file, store it and stop further recursion.
 */
fun findBuildFiles(sourceFolder: File, supportedBuildFileNames: List<String>): List<File> {
    val buildFiles = mutableListOf<File>()
    sourceFolder.walkTopDown()
        .maxDepth(5)
        .onEnter { currentDir ->
            supportedBuildFileNames.forEach {
                val maybeSupportedFile = currentDir.resolve(MAVEN_CONFIGURATION_FILE_NAME)
                if (maybeSupportedFile.exists()) {
                    buildFiles.add(maybeSupportedFile)
                    return@onEnter false
                }
            }
            return@onEnter true
        }.forEach {
            // noop, collects the sequence
        }
    return buildFiles
}

fun parseBuildFile(buildFile: VirtualFile?): String? {
    val absolutePaths = mutableListOf("users/", "system/", "volumes/", "c:\\", "d:\\")
    val alias = System.getProperty("user.home").substringAfterLast(File.separator)
    absolutePaths.add(alias)
    if (buildFile != null && buildFile.exists()) {
        val buildFileContents = buildFile.content().lowercase()
        val detectedPaths: MutableList<String> = mutableListOf()
        for (path in absolutePaths) {
            if (buildFileContents.contains(path)) {
                detectedPaths.add(path)
            }
        }
        if (detectedPaths.size > 0) {
            val warningMessage =
                message(
                    "codemodernizer.chat.message.absolute_path_detected",
                    detectedPaths.size,
                    buildFile.name,
                    detectedPaths.joinToString(", "),
                )
            LOG.info { "CodeTransformation: absolute path potentially in build file" }
            return warningMessage
        }
    }
    return null
}

/**
 * Unzips a zip into a dir. Returns the true when successfully unzips the file pointed to by [zipFilePath] to [destDir]
 */
fun unzipFile(zipFilePath: Path, destDir: Path): Boolean {
    if (!zipFilePath.exists()) return false
    val zipFile = ZipFile(zipFilePath.toFile())
    zipFile.use { file ->
        file.entries().asSequence()
            .filterNot { it.isDirectory }
            .map { zipEntry ->
                val destPath = destDir.resolve(zipEntry.name)
                destPath.createParentDirectories()
                FileOutputStream(destPath.toFile()).use { targetFile ->
                    zipFile.getInputStream(zipEntry).copyTo(targetFile)
                }
            }.toList()
    }
    return true
}

fun parseXmlDependenciesReport(pathToXmlDependency: Path): DependencyUpdatesReport {
    val reportFile = pathToXmlDependency.toFile()
    val xmlMapper = XmlMapper()
    val report = xmlMapper.readValue(reportFile, DependencyUpdatesReport::class.java)
    return report
}

fun validateSctMetadata(sctFile: File?): SqlMetadataValidationResult {
    if (sctFile == null) {
        return SqlMetadataValidationResult(false, "No .sct metadata file found in the provided ZIP.")
    }
    val fileContent = sctFile.readBytes().toString(Charsets.UTF_8)
    val xmlDeserializer = XmlMapper(JacksonXmlModule())
    var sctMetadata: Map<*, *>? = null
    try {
        sctMetadata = xmlDeserializer.readValue(fileContent, Any::class.java) as Map<*, *>
    } catch (e: Exception) {
        getLogger<CodeTransformChatController>().error { "Error parsing .sct metadata file; invalid XML encountered." }
        return SqlMetadataValidationResult(false, "Invalid XML encountered.")
    }

    try {
        val instances = sctMetadata["instances"] as Map<*, *>
        val projectModel = instances["ProjectModel"] as Map<*, *>
        val entities = projectModel["entities"] as Map<*, *>

        val sources = entities["sources"] as Map<*, *>
        val sourceDbServer = sources["DbServer"] as Map<*, *>
        val sourceVendor = (sourceDbServer["vendor"] as String).trim().uppercase()
        if (sourceVendor != "ORACLE") {
            return SqlMetadataValidationResult(false, message("codemodernizer.chat.message.validation.error.invalid_source_db"))
        }

        val sourceServerName = (sourceDbServer["name"] as String).trim()

        val targets = entities["targets"] as Map<*, *>
        val targetDbServer = targets["DbServer"] as Map<*, *>
        val targetVendor = (targetDbServer["vendor"] as String).trim().uppercase()
        if (targetVendor != "AURORA_POSTGRESQL" && targetVendor != "RDS_POSTGRESQL") {
            return SqlMetadataValidationResult(false, message("codemodernizer.chat.message.validation.error.invalid_target_db"))
        }

        val relations = projectModel["relations"] as Map<*, *>
        val serverNodeLocations = relations["server-node-location"] as List<Map<*, *>>
        val schemaNames = mutableSetOf<String>()
        for (serverNodeLocation in serverNodeLocations) {
            val fullNameNodeInfoList = serverNodeLocation["FullNameNodeInfoList"] as Map<*, *>
            val nameParts = fullNameNodeInfoList["nameParts"] as Map<*, *>
            var fullNameNodeInfo = nameParts["FullNameNodeInfo"]
            if (fullNameNodeInfo is Map<*, *>) {
                continue
            } else {
                fullNameNodeInfo = fullNameNodeInfo as List<Map<*, *>>
            }
            fullNameNodeInfo.forEach { node ->
                if ((node["typeNode"] as String).lowercase() == "schema") {
                    schemaNames.add((node["nameNode"] as String).uppercase()) // user will choose one later
                }
            }
        }
        // .sct metadata file is valid, return SqlMetadataValidationResult with all data we parsed
        return SqlMetadataValidationResult(true, "", sourceVendor, targetVendor, sourceServerName, schemaNames)
    } catch (e: Exception) {
        getLogger<CodeTransformChatController>().error { "Error parsing .sct metadata file: $e" }
        return SqlMetadataValidationResult(false, "Sorry, the .sct metadata file you provided appears to be invalid.")
    }
}

fun createFileCopy(originalFile: File, outputPath: Path): File {
    val outputFile = outputPath.toFile()

    originalFile.inputStream().use { inputStream ->
        FileUtil.createParentDirs(outputFile)

        FileOutputStream(outputFile).use { outputStream ->
            inputStream.copyTo(outputStream)
        }
    }

    return outputFile
}

fun setDependencyVersionInPom(pomFile: File, version: String) {
    val existingValue = pomFile.readText()
    val newValue = existingValue.replace(HIL_POM_VERSION_PLACEHOLDER, version)
    pomFile.writeText(newValue)
}

fun getPathToHilArtifactDir(tmpDirPath: Path): Path = tmpDirPath.resolve(HIL_ARTIFACT_DIR_NAME)

fun getPathToHilArtifactPomFolder(tmpDirPath: Path): Path = getPathToHilArtifactDir(tmpDirPath).resolve(HIL_ARTIFACT_POMFOLDER_DIR_NAME)

fun getPathToHilArtifactPomFile(tmpDirPath: Path): Path = getPathToHilArtifactPomFolder(tmpDirPath).resolve(HIL_POM_FILE_NAME)

fun getPathToHilDependencyReportDir(tmpDirPath: Path): Path = tmpDirPath.resolve(HIL_DEPENDENCY_REPORT_DIR_NAME)

fun getPathToHilDependencyReport(tmpDirPath: Path): Path = getPathToHilDependencyReportDir(tmpDirPath).resolve("target/$HIL_DEPENDENCY_REPORT_FILE_NAME")

fun getPathToHilDependenciesRootDir(tmpDirPath: Path): Path = tmpDirPath.resolve(HIL_DEPENDENCY_ROOT_DIR_NAME)

fun getPathToHilUploadZip(tmpDirPath: Path): Path = tmpDirPath.resolve(HIL_UPLOAD_ZIP_NAME)

fun findLineNumberByString(virtualFile: VirtualFile, searchString: String): Int? {
    val text = runReadAction {
        FileDocumentManager.getInstance().getDocument(virtualFile)?.text
    } ?: return null

    val lines = text.split("\n")

    for ((lineNumber, line) in lines.withIndex()) {
        if (line.contains(searchString)) {
            return lineNumber
        }
    }

    return null
}
