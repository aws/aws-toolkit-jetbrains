// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonq.lsp.artifacts

import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.io.DigestUtil
import com.intellij.util.system.CpuArch
import software.aws.toolkits.core.utils.createParentDirectories
import software.aws.toolkits.core.utils.exists
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.zip.ZipFile
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries

fun getToolkitsCommonCacheRoot(): Path = when {
    SystemInfo.isWindows -> {
        Paths.get(System.getenv("LOCALAPPDATA"))
    }
    SystemInfo.isMac -> {
        Paths.get(System.getProperty("user.home"), "Library", "Caches")
    }
    else -> {
        Paths.get(System.getProperty("user.home"), ".cache")
    }
}

fun getCurrentOS(): String = when {
    SystemInfo.isWindows -> "windows"
    SystemInfo.isMac -> "darwin"
    else -> "linux"
}

fun getCurrentArchitecture() = when (CpuArch.CURRENT) {
    CpuArch.X86_64 -> "x64"
    CpuArch.ARM64 -> "arm64"
    else -> "unknown"
}

fun generateMD5Hash(filePath: Path): String {
    val messageDigest = DigestUtil.md5()
    DigestUtil.updateContentHash(messageDigest, filePath)
    return StringUtil.toHexString(messageDigest.digest())
}

fun generateSHA384Hash(filePath: Path): String {
    val messageDigest = MessageDigest.getInstance("SHA-384")
    DigestUtil.updateContentHash(messageDigest, filePath)
    return StringUtil.toHexString(messageDigest.digest())
}

fun getSubFolders(basePath: Path): List<Path> = try {
    basePath.listDirectoryEntries()
        .filter { it.isDirectory() }
} catch (e: Exception) {
    emptyList()
}

fun moveFilesFromSourceToDestination(sourceDir: Path, targetDir: Path) {
    try {
        Files.createDirectories(targetDir.parent)
        Files.move(sourceDir, targetDir, StandardCopyOption.REPLACE_EXISTING)
    } catch (e: Exception) {
        throw IllegalStateException("Failed to move files from $sourceDir to $targetDir", e)
    }
}

fun extractZipFile(zipFilePath: Path, destDir: Path) {
    if (!zipFilePath.exists()) {
        throw FileNotFoundException("Zip file not found: $zipFilePath")
    }

    try {
        ZipFile(zipFilePath.toFile()).use { zipFile ->
            zipFile.entries().asSequence()
                .filterNot { it.isDirectory }
                .map { zipEntry ->
                    val destPath = destDir.resolve(zipEntry.name)
                    destPath.createParentDirectories()
                    FileOutputStream(destPath.toFile()).use { targetFile ->
                        zipFile.getInputStream(zipEntry).copyTo(targetFile)
                    }
                }.toList()
        }
    } catch (e: Exception) {
        throw LspException("Failed to extract zip file: ${e.message}", LspException.ErrorCode.UNZIP_FAILED, cause = e)
    }
}
