// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.core.lsp

import com.intellij.openapi.util.SystemInfo
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Files

class LspFileUtilsTest {

    @Rule
    @JvmField
    val tempFolder = TemporaryFolder()

    @Test
    fun `resolveWithinRoot returns a strict descendant`() {
        val root = tempFolder.newFolder("root").toPath()
        val resolved = LspFileUtils.resolveWithinRoot(root, "nested/file.txt")

        assertThat(resolved.startsWith(root.toAbsolutePath().normalize())).isTrue()
        assertThat(resolved.fileName.toString()).isEqualTo("file.txt")
    }

    @Test
    fun `resolveWithinRoot rejects traversal outside the root`() {
        val root = tempFolder.newFolder("root").toPath()

        assertThatThrownBy { LspFileUtils.resolveWithinRoot(root, "../evil.txt") }
            .isInstanceOfSatisfying(LspInstallException::class.java) {
                assertThat(it.errorCode).isEqualTo(LspInstallException.ErrorCode.EXTRACTION_FAILED)
            }
            .hasMessageContaining("outside install root")
    }

    @Test
    fun `resolveWithinRoot rejects the root itself`() {
        val root = tempFolder.newFolder("root").toPath()

        assertThatThrownBy { LspFileUtils.resolveWithinRoot(root, ".") }
            .isInstanceOf(LspInstallException::class.java)
    }

    @Test
    fun `manifest cache round-trips through atomic write and read and leaves no temp file`() {
        val storageDir = tempFolder.newFolder("storage").toPath()

        LspFileUtils.writeManifestCacheAtomically(storageDir, "manifest.json", """{"k":1}""")

        assertThat(LspFileUtils.readManifestCache(storageDir.resolve("manifest.json"))).isEqualTo("""{"k":1}""")
        assertThat(storageDir.toFile().list()).containsExactly("manifest.json")
    }

    @Test
    fun `readManifestCache returns null when the cache file is absent`() {
        val storageDir = tempFolder.newFolder("storage").toPath()

        assertThat(LspFileUtils.readManifestCache(storageDir.resolve("manifest.json"))).isNull()
    }

    @Test
    fun `deleteRecursively removes a populated directory tree`() {
        val dir = tempFolder.newFolder("victim").toPath()
        Files.createDirectories(dir.resolve("child"))
        Files.writeString(dir.resolve("child").resolve("f.txt"), "x")

        LspFileUtils.deleteRecursively(dir)

        assertThat(Files.exists(dir)).isFalse()
    }

    @Test
    fun `deleteRecursively unlinks a directory symlink without deleting its target`() {
        assumeTrue(SystemInfo.isUnix)
        val target = tempFolder.newFolder("target").toPath()
        val preservedFile = target.resolve("keep.txt")
        Files.writeString(preservedFile, "keep")

        val victim = tempFolder.newFolder("victim").toPath()
        Files.createSymbolicLink(victim.resolve("link-to-target"), target)

        LspFileUtils.deleteRecursively(victim)

        assertThat(Files.exists(victim)).isFalse()
        assertThat(Files.readString(preservedFile)).isEqualTo("keep")
    }

    @Test
    fun `listSubdirectories returns directories and skips files`() {
        val storageDir = tempFolder.newFolder("storage").toPath()
        Files.createDirectories(storageDir.resolve("1.0.0"))
        Files.createDirectories(storageDir.resolve("1.1.0"))
        Files.writeString(storageDir.resolve("manifest.json"), "{}")

        val dirs = LspFileUtils.listSubdirectories(storageDir).map { it.fileName.toString() }

        assertThat(dirs).containsExactlyInAnyOrder("1.0.0", "1.1.0")
    }

    @Test
    fun `listSubdirectories returns empty for a missing directory`() {
        val missing = tempFolder.root.toPath().resolve("nope")

        assertThat(LspFileUtils.listSubdirectories(missing)).isEmpty()
    }

    @Test
    fun `findServerFile locates a server nested one level down`() {
        val versionDir = tempFolder.newFolder("1.0.0").toPath()
        val nested = versionDir.resolve("server")
        Files.createDirectories(nested)
        Files.writeString(nested.resolve("srv.js"), "content")

        assertThat(LspFileUtils.findServerFile(versionDir, "srv.js")).isEqualTo(nested.resolve("srv.js"))
    }

    @Test
    fun `hasRequiredFiles checks files relative to the server directory`() {
        val versionDir = tempFolder.newFolder("1.0.0").toPath()
        Files.writeString(versionDir.resolve("srv.js"), "content")

        assertThat(LspFileUtils.hasRequiredFiles(versionDir, "srv.js", listOf("extras"))).isFalse()

        Files.createDirectories(versionDir.resolve("extras"))
        assertThat(LspFileUtils.hasRequiredFiles(versionDir, "srv.js", listOf("extras"))).isTrue()
    }
}
