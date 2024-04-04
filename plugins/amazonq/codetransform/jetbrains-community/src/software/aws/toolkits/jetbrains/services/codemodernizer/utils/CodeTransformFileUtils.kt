// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.codemodernizer.utils

import com.intellij.openapi.vfs.VirtualFile
import kotlinx.serialization.json.JsonObject
import org.gradle.internal.impldep.com.google.gson.Gson
import software.aws.toolkits.core.utils.createParentDirectories
import software.aws.toolkits.core.utils.exists
import software.aws.toolkits.jetbrains.services.codemodernizer.model.MAVEN_CONFIGURATION_FILE_NAME
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.zip.ZipFile
import kotlin.io.path.Path

fun filterOnlyParentFiles(filePaths: Set<VirtualFile>): List<VirtualFile> {
    if (filePaths.isEmpty()) return listOf()
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

data class ManifestFile(
    val hilType: String?,
    val pomFolderName: String?,
    val sourcePomVersion: String?
)

fun getJsonValuesFromManifestFile(manifestFileVirtualFileReference: String): ManifestFile {
    println("Inside getJsonValuesFromManifestFile $manifestFileVirtualFileReference")
    try {
        val manifestFileContents = File(manifestFileVirtualFileReference).readText()
        val jsonValues = Gson().fromJson(manifestFileContents, JsonObject::class.java)

        return ManifestFile(
            hilType = jsonValues?.get("hilType").toString(),
            pomFolderName = jsonValues?.get("pomFolderName").toString(),
            sourcePomVersion = jsonValues?.get("sourcePomVersion").toString()
        )
    } catch (err: IOException) {
        println("Error parsing manifest.json file $err")
        throw err
    }
}

fun createPomCopy(
    dirname: String,
    pomFileVirtualFileReference: String,
    fileName: String
): String {
    println("In createPomCopy $dirname $pomFileVirtualFileReference $fileName")
    try {
        val newFilePath = Paths.get(dirname, fileName).toString()
        val pomFileContents = Files.readAllBytes(Paths.get(pomFileVirtualFileReference))
        val dirPath = Paths.get(dirname)
        if (!Files.exists(dirPath)) {
            Files.createDirectories(dirPath)
        }
        Files.write(Paths.get(newFilePath), pomFileContents)
        return newFilePath
    } catch (err: Exception) {
        println("Error creating pom copy $err")
        throw err
    }
}

fun replacePomVersion(pomFileVirtualFileReference: String, version: String, delimiter: String) {
    println("In replacePomVersion $pomFileVirtualFileReference $version $delimiter")
    try {
        val pomFileText = File(pomFileVirtualFileReference).readText()
        val pomFileTextWithNewVersion = pomFileText.replace(delimiter, version)
        File(pomFileVirtualFileReference).writeText(pomFileTextWithNewVersion)
    } catch (err: IOException) {
        println("Error replacing pom version $err")
        throw err
    }
}

fun parseXmlDependenciesReport(): Array<String> = arrayOf("12.04.1", "12.05.2")
