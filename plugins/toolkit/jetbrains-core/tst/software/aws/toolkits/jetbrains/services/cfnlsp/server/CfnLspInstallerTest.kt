// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cfnlsp.server

import org.assertj.core.api.Assertions.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Files
import java.nio.file.Path

class CfnLspInstallerTest {

    @Rule
    @JvmField
    val tempFolder = TemporaryFolder()

    @Test
    fun `findCachedServer returns null when storage dir does not exist`() {
        val installer = installerWithDir("non-existent")
        assertThat(installer.findCachedServerForTest()).isNull()
    }

    @Test
    fun `findCachedServer returns null when no server files present`() {
        val storageDir = tempFolder.newFolder("lsp-storage").toPath()
        Files.createDirectories(storageDir.resolve("1.2.0"))

        assertThat(CfnLspInstaller(storageDir).findCachedServerForTest()).isNull()
    }

    @Test
    fun `findCachedServer returns highest compatible version`() {
        val storageDir = tempFolder.newFolder("lsp-storage").toPath()
        createServerVersion(storageDir, "1.0.0")
        createServerVersion(storageDir, "1.2.0")
        createServerVersion(storageDir, "1.4.0")

        val result = CfnLspInstaller(storageDir).findCachedServerForTest()
        assertThat(result?.parent?.fileName.toString()).isEqualTo("1.4.0")
    }

    @Test
    fun `findCachedServer uses semver not lexicographic ordering`() {
        val storageDir = tempFolder.newFolder("lsp-storage").toPath()
        createServerVersion(storageDir, "1.9.0")
        createServerVersion(storageDir, "1.10.0")

        val result = CfnLspInstaller(storageDir).findCachedServerForTest()
        assertThat(result?.parent?.fileName.toString()).isEqualTo("1.10.0")
    }

    @Test
    fun `findCachedServer excludes versions outside supported range`() {
        val storageDir = tempFolder.newFolder("lsp-storage").toPath()
        createServerVersion(storageDir, "1.4.0")
        createServerVersion(storageDir, "2.0.0")
        createServerVersion(storageDir, "3.0.0")

        // Default range is <2.0.0
        val result = CfnLspInstaller(storageDir).findCachedServerForTest()
        assertThat(result?.parent?.fileName.toString()).isEqualTo("1.4.0")
    }

    @Test
    fun `findCachedServer returns null when all versions outside range`() {
        val storageDir = tempFolder.newFolder("lsp-storage").toPath()
        createServerVersion(storageDir, "2.0.0")
        createServerVersion(storageDir, "3.0.0")

        assertThat(CfnLspInstaller(storageDir).findCachedServerForTest()).isNull()
    }

    @Test
    fun `findCachedServer skips directories with unparseable names`() {
        val storageDir = tempFolder.newFolder("lsp-storage").toPath()
        createServerVersion(storageDir, "not-a-version")
        createServerVersion(storageDir, "1.2.0")

        val result = CfnLspInstaller(storageDir).findCachedServerForTest()
        assertThat(result?.parent?.fileName.toString()).isEqualTo("1.2.0")
    }

    // --- cleanupOldVersions ---

    @Test
    fun `cleanupOldVersions keeps current version and one fallback`() {
        val storageDir = tempFolder.newFolder("lsp-storage").toPath()
        createServerVersion(storageDir, "1.0.0")
        createServerVersion(storageDir, "1.2.0")
        createServerVersion(storageDir, "1.4.0")

        CfnLspInstaller(storageDir).cleanupOldVersionsForTest("1.4.0")

        assertThat(Files.exists(storageDir.resolve("1.4.0"))).isTrue() // current
        assertThat(Files.exists(storageDir.resolve("1.2.0"))).isTrue() // fallback
        assertThat(Files.exists(storageDir.resolve("1.0.0"))).isFalse() // removed
    }

    @Test
    fun `cleanupOldVersions removes versions outside supported range`() {
        val storageDir = tempFolder.newFolder("lsp-storage").toPath()
        createServerVersion(storageDir, "1.4.0")
        createServerVersion(storageDir, "2.0.0")

        CfnLspInstaller(storageDir).cleanupOldVersionsForTest("1.4.0")

        assertThat(Files.exists(storageDir.resolve("1.4.0"))).isTrue()
        assertThat(Files.exists(storageDir.resolve("2.0.0"))).isFalse()
    }

    @Test
    fun `cleanupOldVersions preserves only current when no valid fallback`() {
        val storageDir = tempFolder.newFolder("lsp-storage").toPath()
        createServerVersion(storageDir, "1.4.0")

        CfnLspInstaller(storageDir).cleanupOldVersionsForTest("1.4.0")

        assertThat(Files.exists(storageDir.resolve("1.4.0"))).isTrue()
    }

    @Test
    fun `cleanupOldVersions handles non-existent storage dir gracefully`() {
        val installer = installerWithDir("non-existent")
        // Should not throw
        installer.cleanupOldVersionsForTest("1.0.0")
    }

    @Test
    fun `cleanupOldVersions picks highest compatible version as fallback`() {
        val storageDir = tempFolder.newFolder("lsp-storage").toPath()
        createServerVersion(storageDir, "1.0.0")
        createServerVersion(storageDir, "1.1.0")
        createServerVersion(storageDir, "1.2.0")
        createServerVersion(storageDir, "1.3.0")
        createServerVersion(storageDir, "1.4.0")

        CfnLspInstaller(storageDir).cleanupOldVersionsForTest("1.4.0")

        assertThat(Files.exists(storageDir.resolve("1.4.0"))).isTrue() // current
        assertThat(Files.exists(storageDir.resolve("1.3.0"))).isTrue() // fallback (highest below current)
        assertThat(Files.exists(storageDir.resolve("1.2.0"))).isFalse()
        assertThat(Files.exists(storageDir.resolve("1.1.0"))).isFalse()
        assertThat(Files.exists(storageDir.resolve("1.0.0"))).isFalse()
    }

    @Test
    fun `cleanupOldVersions skips unparseable directory names`() {
        val storageDir = tempFolder.newFolder("lsp-storage").toPath()
        createServerVersion(storageDir, "1.4.0")
        createServerVersion(storageDir, "temp-download")

        CfnLspInstaller(storageDir).cleanupOldVersionsForTest("1.4.0")

        assertThat(Files.exists(storageDir.resolve("1.4.0"))).isTrue()
        // unparseable dirs get cleaned up since they're not in the keep set
        assertThat(Files.exists(storageDir.resolve("temp-download"))).isFalse()
    }

    // --- hash utilities ---

    @Test
    fun `parseHashString extracts algorithm and hash`() {
        assertThat(CfnLspInstaller.parseHashString("sha256:abc123def456"))
            .isEqualTo("sha256" to "abc123def456")
    }

    @Test
    fun `parseHashString returns null for invalid format`() {
        assertThat(CfnLspInstaller.parseHashString("invalidhash")).isNull()
    }

    @Test
    fun `computeHash calculates sha256 correctly`() {
        val hash = CfnLspInstaller.computeHash("test data".toByteArray(), "sha256")
        assertThat(hash).isEqualTo("916f0027a575074ce72a331777c3478d6513f786a591bd892da1a577bf2335f9")
    }

    @Test
    fun `computeHash calculates sha384 correctly`() {
        val hash = CfnLspInstaller.computeHash("test data".toByteArray(), "sha384")
        assertThat(hash).hasSize(96) // SHA-384 produces 96 hex characters
    }

    // --- helpers ---

    private fun createServerVersion(storageDir: Path, version: String) {
        val dir = storageDir.resolve(version)
        Files.createDirectories(dir)
        Files.createFile(dir.resolve(CfnLspServerConfig.SERVER_FILE))
    }

    private fun installerWithDir(name: String) =
        CfnLspInstaller(tempFolder.root.toPath().resolve(name))

    private fun CfnLspInstaller.findCachedServerForTest(): Path? {
        val method = this::class.java.getDeclaredMethod("findCachedServer")
        method.isAccessible = true
        return method.invoke(this) as? Path
    }

    private fun CfnLspInstaller.cleanupOldVersionsForTest(currentVersion: String) {
        val method = this::class.java.getDeclaredMethod("cleanupOldVersions", String::class.java)
        method.isAccessible = true
        method.invoke(this, currentVersion)
    }
}
