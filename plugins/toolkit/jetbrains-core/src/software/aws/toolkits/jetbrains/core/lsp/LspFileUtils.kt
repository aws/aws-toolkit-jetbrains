// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.core.lsp

import software.aws.toolkits.jetbrains.utils.ZipDecompressor
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.BasicFileAttributes
import java.util.UUID

internal object LspFileUtils {

    fun exists(path: Path): Boolean = Files.exists(path)

    fun resolveWithinRoot(root: Path, relativePath: String): Path {
        val normalizedRoot = root.toAbsolutePath().normalize()
        val destination = normalizedRoot.resolve(relativePath).normalize()
        if (destination == normalizedRoot || !destination.startsWith(normalizedRoot)) {
            throw LspInstallException(
                "Refusing to write content outside install root: $relativePath",
                LspInstallException.ErrorCode.EXTRACTION_FAILED
            )
        }
        return destination
    }

    fun preflightContents(targetDir: Path, contents: List<Pair<LspContentEntry, ByteArray>>) {
        for ((content, bytes) in contents) {
            if (isZip(content.filename)) {
                ZipDecompressor(bytes).use { it.validate(targetDir.toFile()) }
            } else {
                resolveWithinRoot(targetDir, content.filename)
            }
        }
    }

    fun writeContents(targetDir: Path, contents: List<Pair<LspContentEntry, ByteArray>>) {
        Files.createDirectories(targetDir)
        for ((content, bytes) in contents) {
            if (isZip(content.filename)) {
                ZipDecompressor(bytes).use { it.extract(targetDir.toFile()) }
            } else {
                val destination = resolveWithinRoot(targetDir, content.filename)
                Files.createDirectories(destination.parent)
                Files.write(destination, bytes)
            }
        }
    }

    fun findServerFile(versionDir: Path, serverFilename: String): Path? {
        val direct = versionDir.resolve(serverFilename)
        if (Files.exists(direct)) return direct

        if (!Files.exists(versionDir)) return null
        return Files.list(versionDir).use { stream ->
            stream.filter { Files.isDirectory(it) }
                .map { it.resolve(serverFilename) }
                .filter { Files.exists(it) }
                .findFirst()
                .orElse(null)
        }
    }

    fun hasRequiredFiles(versionDir: Path, serverFilename: String, requiredFiles: List<String>): Boolean {
        val serverFile = findServerFile(versionDir, serverFilename) ?: return false
        val effectiveRoot = serverFile.parent
        return requiredFiles.all { Files.exists(effectiveRoot.resolve(it)) }
    }

    fun requireServerAndRequiredFiles(versionDir: Path, serverFilename: String, requiredFiles: List<String>): Path {
        val serverFile = findServerFile(versionDir, serverFilename)
            ?: throw LspInstallException(
                "Server file '$serverFilename' not found after extraction",
                LspInstallException.ErrorCode.EXTRACTION_FAILED
            )
        val effectiveRoot = serverFile.parent

        for (requiredFile in requiredFiles) {
            if (!Files.exists(effectiveRoot.resolve(requiredFile))) {
                throw LspInstallException(
                    "Required file '$requiredFile' not found after install (checked relative to ${effectiveRoot.fileName})",
                    LspInstallException.ErrorCode.EXTRACTION_FAILED
                )
            }
        }
        return serverFile
    }

    fun listSubdirectories(dir: Path): List<Path> {
        if (!Files.exists(dir)) return emptyList()
        return Files.list(dir).use { stream ->
            stream.filter { Files.isDirectory(it) }.toList()
        }
    }

    fun deleteRecursively(path: Path) {
        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) return
        // Delete bottom-up with NIO so a failure surfaces as an IOException instead of the silent Boolean
        // of kotlin.io.deleteRecursively. The walk does not follow links, so a symlinked directory is
        // reported to visitFile and unlinked without descending into (or deleting) its target.
        Files.walkFileTree(
            path,
            object : SimpleFileVisitor<Path>() {
                override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                    Files.delete(file)
                    return FileVisitResult.CONTINUE
                }

                override fun postVisitDirectory(dir: Path, exc: IOException?): FileVisitResult {
                    exc?.let { throw it }
                    Files.delete(dir)
                    return FileVisitResult.CONTINUE
                }
            },
        )
    }

    fun readManifestCache(cachePath: Path): String? =
        if (Files.exists(cachePath)) Files.readString(cachePath) else null

    fun writeManifestCacheAtomically(storageDir: Path, cacheFileName: String, json: String) {
        Files.createDirectories(storageDir)
        val cachePath = storageDir.resolve(cacheFileName)
        val tmpPath = storageDir.resolve("$cacheFileName.tmp.${UUID.randomUUID()}")
        try {
            Files.writeString(tmpPath, json)
            try {
                Files.move(tmpPath, cachePath, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(tmpPath, cachePath, StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            runCatching { Files.deleteIfExists(tmpPath) }
        }
    }

    private fun isZip(filename: String): Boolean = filename.endsWith(".zip", ignoreCase = true)
}
