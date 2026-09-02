// Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.utils

import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.io.FileUtil
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry
import org.apache.commons.compress.archivers.zip.ZipFile
import org.apache.commons.compress.utils.SeekableInMemoryByteChannel
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermission

class ZipDecompressor(sourceBytes: ByteArray) : AutoCloseable {
    private val zipFile = ZipFile(SeekableInMemoryByteChannel(sourceBytes))
    private val zipEntries = zipFile.entries.toList()

    fun extract(destination: File) {
        validate(destination)
        zipEntries.forEach {
            val outputFile = outputFile(destination, it.name)
            // TODO: Handle symlink if we ever need it
            when {
                it.isDirectory -> FileUtil.createDirectory(outputFile)
                else -> createFile(outputFile, it)
            }
        }
    }

    fun validate(destination: File) {
        zipEntries.forEach { outputFile(destination, it.name) }
    }

    private fun createFile(outputFile: File, zipEntry: ZipArchiveEntry) {
        zipFile.getInputStream(zipEntry).use { zipStream ->
            FileUtil.createParentDirs(outputFile)

            FileOutputStream(outputFile).use { outputStream ->
                zipStream.copyTo(outputStream)
            }

            if (SystemInfo.isUnix) {
                Files.setPosixFilePermissions(outputFile.toPath(), convertPermissions(zipEntry.unixMode))
            }
        }
    }

    private fun outputFile(outputDir: File, entryName: String): File {
        val root = outputDir.toPath().toAbsolutePath().normalize()
        val candidate = root.resolve(entryName.replace('\\', '/')).normalize()

        if (candidate == root || !candidate.startsWith(root)) {
            throw IOException("Zip entry attempting to escape destination directory: $entryName")
        }

        return candidate.toFile()
    }

    private fun convertPermissions(mode: Int): Set<PosixFilePermission> {
        val permissions = mutableSetOf<PosixFilePermission>()
        if ((mode and 0b100_000_000) > 0) {
            permissions.add(PosixFilePermission.OWNER_READ)
        }
        if ((mode and 0b010_000_000) > 0) {
            permissions.add(PosixFilePermission.OWNER_WRITE)
        }
        if ((mode and 0b001_000_000) > 0) {
            permissions.add(PosixFilePermission.OWNER_EXECUTE)
        }
        if ((mode and 0b000_100_000) > 0) {
            permissions.add(PosixFilePermission.GROUP_READ)
        }
        if ((mode and 0b000_010_000) > 0) {
            permissions.add(PosixFilePermission.GROUP_WRITE)
        }
        if ((mode and 0b000_001_000) > 0) {
            permissions.add(PosixFilePermission.GROUP_EXECUTE)
        }
        if ((mode and 0b000_000_100) > 0) {
            permissions.add(PosixFilePermission.OTHERS_READ)
        }
        if ((mode and 0b000_000_010) > 0) {
            permissions.add(PosixFilePermission.OTHERS_WRITE)
        }
        if ((mode and 0b000_000_001) > 0) {
            permissions.add(PosixFilePermission.OTHERS_EXECUTE)
        }
        return permissions
    }

    override fun close() {
        zipFile.close()
    }
}
