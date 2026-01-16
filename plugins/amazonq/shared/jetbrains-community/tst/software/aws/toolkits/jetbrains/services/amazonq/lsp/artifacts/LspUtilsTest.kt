// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonq.lsp.artifacts

import com.intellij.testFramework.utils.io.createDirectory
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import software.amazon.q.core.utils.ZIP_PROPERTY_POSIX
import software.amazon.q.core.utils.hasPosixFilePermissions
import software.amazon.q.core.utils.putNextEntry
import software.amazon.q.core.utils.test.assertPosixPermissions
import software.amazon.q.core.utils.writeText
import software.amazon.q.jetbrains.utils.satisfiesKt
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.PosixFilePermissions
import java.util.zip.ZipOutputStream
import kotlin.io.path.isRegularFile
import kotlin.io.path.setPosixFilePermissions

class LspUtilsTest {
    @Test
    fun `extractZipFile works`(@TempDir tempDir: Path) {
        val source = tempDir.resolve("source").also { it.createDirectory() }
        val target = tempDir.resolve("target").also { it.createDirectory() }

        source.resolve("file1").writeText("contents1")
        source.resolve("file2").writeText("contents2")
        source.resolve("file3").writeText("contents3")

        val sourceZip = tempDir.resolve("source.zip")
        ZipOutputStream(Files.newOutputStream(sourceZip)).use { zip ->
            Files.walk(source).use { paths ->
                paths
                    .filter { it.isRegularFile() }
                    .forEach {
                        zip.putNextEntry(source.relativize(it).toString(), it)
                    }
                val precedingSlashFile = source.resolve("file4").also { it.writeText("contents4") }
                zip.putNextEntry("/${source.relativize(precedingSlashFile)}", precedingSlashFile)
            }
        }

        extractZipFile(sourceZip, target)

        assertThat(target).satisfiesKt {
            val files = Files.list(it).use { stream -> stream.toList() }
            assertThat(files.size).isEqualTo(4)
            assertThat(target.resolve("file1")).hasContent("contents1")
            assertThat(target.resolve("file2")).hasContent("contents2")
            assertThat(target.resolve("file3")).hasContent("contents3")
            assertThat(target.resolve("file4")).hasContent("contents4")
        }
    }

    @Test
    fun `extractZipFile respects posix`(@TempDir tempDir: Path) {
        assumeTrue(tempDir.hasPosixFilePermissions())

        val source = tempDir.resolve("source").also { it.createDirectory() }
        val target = tempDir.resolve("target").also { it.createDirectory() }

        source.resolve("regularFile").also {
            it.writeText("contents1")
            it.setPosixFilePermissions(PosixFilePermissions.fromString("rw-r--r--"))
        }
        source.resolve("executableFile").also {
            it.writeText("contents2")
            it.setPosixFilePermissions(PosixFilePermissions.fromString("rwxr-xr-x"))
        }

        val sourceZip = tempDir.resolve("source.zip")
        FileSystems.newFileSystem(
            sourceZip,
            mapOf(
                "create" to true,
                ZIP_PROPERTY_POSIX to true,
            )
        ).use { zipfs ->
            Files.walk(source).use { paths ->
                paths
                    .filter { it.isRegularFile() }
                    .forEach { file ->
                        Files.copy(file, zipfs.getPath("/").resolve(source.relativize(file).toString()), StandardCopyOption.COPY_ATTRIBUTES)
                    }
            }
        }

        extractZipFile(sourceZip, target)

        assertThat(target).satisfiesKt {
            val files = Files.list(it).use { stream -> stream.toList() }
            assertThat(files.size).isEqualTo(2)
            assertPosixPermissions(target.resolve("regularFile"), "rw-r--r--")
            assertPosixPermissions(target.resolve("executableFile"), "rwxr-xr-x")
        }
    }
}
