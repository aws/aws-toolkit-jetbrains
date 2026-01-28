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
        val nonExistentDir = tempFolder.root.toPath().resolve("non-existent")
        val installer = CfnLspInstaller(nonExistentDir)

        assertThat(installer.findCachedServerForTest()).isNull()
    }

    @Test
    fun `findCachedServer returns null when server file not present`() {
        val storageDir = tempFolder.newFolder("lsp-storage").toPath()
        val installer = CfnLspInstaller(storageDir)

        assertThat(installer.findCachedServerForTest()).isNull()
    }

    @Test
    fun `findCachedServer returns path when server file exists`() {
        val storageDir = tempFolder.newFolder("lsp-storage").toPath()
        val versionDir = storageDir.resolve("v1.0.0")
        Files.createDirectories(versionDir)
        val serverFile = versionDir.resolve(CfnLspServerConfig.SERVER_FILE)
        Files.createFile(serverFile)

        val installer = CfnLspInstaller(storageDir)

        assertThat(installer.findCachedServerForTest()).isEqualTo(serverFile)
    }

    @Test
    fun `defaultStorageDir returns path under toolkit cache`() {
        val defaultDir = CfnLspInstaller.defaultStorageDir()

        assertThat(defaultDir.toString()).contains("cloudformation-lsp")
        assertThat(defaultDir.toString()).contains("toolkits")
    }

    @Test
    fun `cleanupOldVersions removes directories not matching current version`() {
        val storageDir = tempFolder.newFolder("lsp-storage").toPath()
        val oldVersion = storageDir.resolve("v1.0.0")
        val currentVersion = storageDir.resolve("v2.0.0")
        Files.createDirectories(oldVersion)
        Files.createDirectories(currentVersion)
        Files.createFile(oldVersion.resolve("some-file.txt"))

        val installer = CfnLspInstaller(storageDir)
        installer.cleanupOldVersionsForTest("v2.0.0")

        assertThat(Files.exists(oldVersion)).isFalse()
        assertThat(Files.exists(currentVersion)).isTrue()
    }

    @Test
    fun `cleanupOldVersions preserves current version directory`() {
        val storageDir = tempFolder.newFolder("lsp-storage").toPath()
        val currentVersion = storageDir.resolve("v2.0.0")
        Files.createDirectories(currentVersion)
        val serverFile = currentVersion.resolve(CfnLspServerConfig.SERVER_FILE)
        Files.createFile(serverFile)

        val installer = CfnLspInstaller(storageDir)
        installer.cleanupOldVersionsForTest("v2.0.0")

        assertThat(Files.exists(currentVersion)).isTrue()
        assertThat(Files.exists(serverFile)).isTrue()
    }

    @Test
    fun `cleanupOldVersions handles non-existent storage dir gracefully`() {
        val nonExistentDir = tempFolder.root.toPath().resolve("non-existent")
        val installer = CfnLspInstaller(nonExistentDir)

        // Should not throw
        installer.cleanupOldVersionsForTest("v1.0.0")
    }

    @Test
    fun `cleanupOldVersions removes multiple old versions`() {
        val storageDir = tempFolder.newFolder("lsp-storage").toPath()
        val v1 = storageDir.resolve("v1.0.0")
        val v2 = storageDir.resolve("v1.5.0")
        val current = storageDir.resolve("v2.0.0")
        listOf(v1, v2, current).forEach { Files.createDirectories(it) }

        val installer = CfnLspInstaller(storageDir)
        installer.cleanupOldVersionsForTest("v2.0.0")

        assertThat(Files.exists(v1)).isFalse()
        assertThat(Files.exists(v2)).isFalse()
        assertThat(Files.exists(current)).isTrue()
    }

    @Test
    fun `parseHashString extracts algorithm and hash`() {
        val result = CfnLspInstaller.parseHashString("sha256:abc123def456")

        assertThat(result).isEqualTo("sha256" to "abc123def456")
    }

    @Test
    fun `parseHashString returns null for invalid format`() {
        assertThat(CfnLspInstaller.parseHashString("invalidhash")).isNull()
    }

    @Test
    fun `computeHash calculates sha256 correctly`() {
        val data = "test data".toByteArray()
        val hash = CfnLspInstaller.computeHash(data, "sha256")

        // Known SHA-256 hash of "test data"
        assertThat(hash).isEqualTo("916f0027a575074ce72a331777c3478d6513f786a591bd892da1a577bf2335f9")
    }

    @Test
    fun `computeHash calculates sha384 correctly`() {
        val data = "test data".toByteArray()
        val hash = CfnLspInstaller.computeHash(data, "sha384")

        assertThat(hash).hasSize(96) // SHA-384 produces 96 hex characters
    }

    private fun CfnLspInstaller.findCachedServerForTest(): Path? = try {
        val method = this::class.java.getDeclaredMethod("findCachedServer")
        method.isAccessible = true
        method.invoke(this) as? Path
    } catch (e: Exception) {
        null
    }

    private fun CfnLspInstaller.cleanupOldVersionsForTest(currentVersion: String) {
        val method = this::class.java.getDeclaredMethod("cleanupOldVersions", String::class.java)
        method.isAccessible = true
        method.invoke(this, currentVersion)
    }
}
