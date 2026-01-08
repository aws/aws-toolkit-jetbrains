// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cfnlsp.server

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Files

class CfnLspInstallerTest {

    @Rule
    @JvmField
    val tempFolder = TemporaryFolder()

    @Test
    fun `findExistingServer returns null when storage dir does not exist`() {
        val nonExistentDir = tempFolder.root.toPath().resolve("non-existent")
        val installer = CfnLspInstaller(nonExistentDir)

        assertThat(installer.getServerPathOrNull()).isNull()
    }

    @Test
    fun `findExistingServer returns null when server file not present`() {
        val storageDir = tempFolder.newFolder("lsp-storage").toPath()
        val installer = CfnLspInstaller(storageDir)

        assertThat(installer.getServerPathOrNull()).isNull()
    }

    @Test
    fun `findExistingServer returns path when server file exists`() {
        val storageDir = tempFolder.newFolder("lsp-storage").toPath()
        val versionDir = storageDir.resolve("v1.0.0")
        Files.createDirectories(versionDir)
        val serverFile = versionDir.resolve(CfnLspServerConfig.SERVER_FILE)
        Files.createFile(serverFile)

        val installer = CfnLspInstaller(storageDir)

        assertThat(installer.getServerPathOrNull()).isEqualTo(serverFile)
    }

    @Test
    fun `defaultStorageDir returns path under toolkit cache`() {
        val defaultDir = CfnLspInstaller.defaultStorageDir()

        assertThat(defaultDir.toString()).contains("cloudformation-lsp")
        assertThat(defaultDir.toString()).contains("toolkits")
    }

    // Helper extension to test findExistingServer without triggering download
    private fun CfnLspInstaller.getServerPathOrNull(): java.nio.file.Path? = try {
        val field = this::class.java.getDeclaredMethod("findExistingServer")
        field.isAccessible = true
        field.invoke(this) as? java.nio.file.Path
    } catch (e: Exception) {
        null
    }
}
