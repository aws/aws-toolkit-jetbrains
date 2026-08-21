// Copyright 2026 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.utils

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException

class ZipDecompressorTest {

    @Rule
    @JvmField
    val tempFolder = TemporaryFolder()

    @Test
    fun `extracts valid nested entries into the destination`() {
        val destination = tempFolder.newFolder("dest")
        val zip = nestedZip(
            "nested/inner.txt" to "hello",
            "nested/deeper/leaf.txt" to "world",
        )

        extract(zip, destination)

        assertThat(File(destination, "nested")).isDirectory()
        assertThat(File(destination, "nested/inner.txt")).exists().hasContent("hello")
        assertThat(File(destination, "nested/deeper/leaf.txt")).exists().hasContent("world")
    }

    @Test
    fun `rejects parent directory traversal`() {
        val destination = tempFolder.newFolder("dest")
        val escaped = File(tempFolder.root, "evil.txt")

        assertThatThrownBy { extract(singleEntryZip("../evil.txt", "pwned"), destination) }
            .isInstanceOf(IOException::class.java)
            .hasMessageContaining("../evil.txt")

        assertThat(escaped).doesNotExist()
        assertThat(destination.listFiles()).isNullOrEmpty()
    }

    @Test
    fun `rejects nested traversal that escapes the destination`() {
        val destination = tempFolder.newFolder("dest")
        val escaped = File(tempFolder.root, "evil.txt")

        assertThatThrownBy { extract(singleEntryZip("foo/../../evil.txt", "pwned"), destination) }
            .isInstanceOf(IOException::class.java)
            .hasMessageContaining("foo/../../evil.txt")

        assertThat(escaped).doesNotExist()
        assertThat(destination.listFiles()).isNullOrEmpty()
    }

    @Test
    fun `rejects backslash traversal on every OS`() {
        val destination = tempFolder.newFolder("dest")
        val escaped = File(tempFolder.root, "evil.txt")
        // The leading forward slash stops Commons Compress from rewriting the backslashes while
        // authoring the fixture, so the decompressor sees the raw Windows-style separators an
        // external tool would emit. On Unix this only escapes if backslashes are treated as separators.
        val entryName = """sub/..\..\evil.txt"""

        assertThatThrownBy { extract(singleEntryZip(entryName, "pwned"), destination) }
            .isInstanceOf(IOException::class.java)
            .hasMessageContaining(entryName)

        assertThat(escaped).doesNotExist()
        assertThat(destination.listFiles()).isNullOrEmpty()
    }

    @Test
    fun `rejects absolute path escape`() {
        val destination = tempFolder.newFolder("dest")
        val escaped = File(tempFolder.newFolder("outside"), "evil.txt")
        val entryName = escaped.absolutePath
        // ZIP stores entry names with forward slashes, so on Windows the archive spelling differs
        // from the OS-native absolute path (backslashes). The decompressor reports the stored name,
        // so assert against the normalized spelling rather than the platform path.
        val archiveEntryName = entryName.replace('\\', '/')

        assertThatThrownBy { extract(singleEntryZip(entryName, "pwned"), destination) }
            .isInstanceOf(IOException::class.java)
            .hasMessageContaining(archiveEntryName)

        assertThat(escaped).doesNotExist()
    }

    @Test
    fun `extract writes nothing when a safe entry precedes a traversal entry`() {
        val destination = tempFolder.newFolder("dest")
        val escaped = File(tempFolder.root, "evil.txt")
        val zip = twoEntryZip("safe.txt" to "safe", "../evil.txt" to "pwned")

        assertThatThrownBy { extract(zip, destination) }
            .isInstanceOf(IOException::class.java)
            .hasMessageContaining("../evil.txt")

        // The archive is preflighted before any write, so the leading safe entry is not written.
        assertThat(File(destination, "safe.txt")).doesNotExist()
        assertThat(escaped).doesNotExist()
        assertThat(destination.listFiles()).isNullOrEmpty()
    }

    @Test
    fun `validate rejects an escaping entry without writing anything`() {
        val destination = tempFolder.newFolder("dest")
        val zip = twoEntryZip("safe.txt" to "safe", "../evil.txt" to "pwned")

        ZipDecompressor(zip).use { decompressor ->
            assertThatThrownBy { decompressor.validate(destination) }
                .isInstanceOf(IOException::class.java)
                .hasMessageContaining("../evil.txt")
        }

        assertThat(File(destination, "safe.txt")).doesNotExist()
        assertThat(destination.listFiles()).isNullOrEmpty()
    }

    @Test
    fun `validate accepts an archive whose entries all stay within the destination`() {
        val destination = tempFolder.newFolder("dest")
        val zip = twoEntryZip("a.txt" to "a", "sub/b.txt" to "b")

        ZipDecompressor(zip).use { it.validate(destination) }

        // Validation writes nothing on its own.
        assertThat(destination.listFiles()).isNullOrEmpty()
    }

    private fun extract(zipBytes: ByteArray, destination: File) {
        ZipDecompressor(zipBytes).use { it.extract(destination) }
    }

    private fun singleEntryZip(name: String, content: String): ByteArray {
        val baos = ByteArrayOutputStream()
        ZipArchiveOutputStream(baos).use { zos ->
            zos.putArchiveEntry(ZipArchiveEntry(name).apply { unixMode = FILE_MODE })
            zos.write(content.toByteArray())
            zos.closeArchiveEntry()
        }
        return baos.toByteArray()
    }

    private fun twoEntryZip(vararg entries: Pair<String, String>): ByteArray {
        val baos = ByteArrayOutputStream()
        ZipArchiveOutputStream(baos).use { zos ->
            for ((name, content) in entries) {
                zos.putArchiveEntry(ZipArchiveEntry(name).apply { unixMode = FILE_MODE })
                zos.write(content.toByteArray())
                zos.closeArchiveEntry()
            }
        }
        return baos.toByteArray()
    }

    private fun nestedZip(vararg entries: Pair<String, String>): ByteArray {
        val baos = ByteArrayOutputStream()
        ZipArchiveOutputStream(baos).use { zos ->
            val createdDirs = mutableSetOf<String>()
            for ((path, content) in entries) {
                val parts = path.split("/")
                for (i in 1 until parts.size) {
                    val dirPath = parts.subList(0, i).joinToString("/") + "/"
                    if (createdDirs.add(dirPath)) {
                        zos.putArchiveEntry(ZipArchiveEntry(dirPath).apply { unixMode = DIR_MODE })
                        zos.closeArchiveEntry()
                    }
                }
                zos.putArchiveEntry(ZipArchiveEntry(path).apply { unixMode = FILE_MODE })
                zos.write(content.toByteArray())
                zos.closeArchiveEntry()
            }
        }
        return baos.toByteArray()
    }

    private companion object {
        // Archive entry modes so extracted files/directories get readable/searchable POSIX
        // permissions; entries default to mode 0 (no permissions), which would fail readback on Unix.
        val FILE_MODE = "644".toInt(8)
        val DIR_MODE = "755".toInt(8)
    }
}
