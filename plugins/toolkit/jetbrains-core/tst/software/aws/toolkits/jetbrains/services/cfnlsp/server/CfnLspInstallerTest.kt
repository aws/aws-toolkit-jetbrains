// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cfnlsp.server

import org.assertj.core.api.Assertions.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import software.aws.toolkits.jetbrains.core.lsp.BaseLspInstaller
import software.aws.toolkits.jetbrains.core.lsp.LspInstallerConfig
import software.aws.toolkits.jetbrains.core.lsp.LspManifest
import software.aws.toolkits.jetbrains.core.lsp.ManifestAdapter
import software.aws.toolkits.jetbrains.core.lsp.SemVerParser
import java.nio.file.Files
import java.nio.file.Path

class CfnLspInstallerTest {

    @Rule
    @JvmField
    val tempFolder = TemporaryFolder()

    private val lspName = "cfn"

    @Test
    fun `findCachedServer returns null when storage dir does not exist`() {
        val baseRoot = tempFolder.root.toPath().resolve("nonexistent")
        val installer = TestableInstaller(baseRoot)
        assertThat(installer.findCachedServer()).isNull()
    }

    @Test
    fun `findCachedServer returns null when no server files present`() {
        val (baseRoot, storageDir) = createLayout()
        Files.createDirectories(storageDir.resolve("1.2.0"))

        assertThat(TestableInstaller(baseRoot).findCachedServer()).isNull()
    }

    @Test
    fun `findCachedServer returns highest compatible version`() {
        val (baseRoot, storageDir) = createLayout()
        createServerVersion(storageDir, "1.0.0")
        createServerVersion(storageDir, "1.2.0")
        createServerVersion(storageDir, "1.4.0")

        val result = TestableInstaller(baseRoot).findCachedServer()
        assertThat(result?.parent?.fileName.toString()).isEqualTo("1.4.0")
    }

    @Test
    fun `findCachedServer uses semver not lexicographic ordering`() {
        val (baseRoot, storageDir) = createLayout()
        createServerVersion(storageDir, "1.9.0")
        createServerVersion(storageDir, "1.10.0")

        val result = TestableInstaller(baseRoot).findCachedServer()
        assertThat(result?.parent?.fileName.toString()).isEqualTo("1.10.0")
    }

    @Test
    fun `findCachedServer excludes versions outside supported range`() {
        val (baseRoot, storageDir) = createLayout()
        createServerVersion(storageDir, "1.4.0")
        createServerVersion(storageDir, "2.0.0")
        createServerVersion(storageDir, "3.0.0")

        val result = TestableInstaller(baseRoot).findCachedServer()
        assertThat(result?.parent?.fileName.toString()).isEqualTo("1.4.0")
    }

    @Test
    fun `findCachedServer returns null when all versions outside range`() {
        val (baseRoot, storageDir) = createLayout()
        createServerVersion(storageDir, "2.0.0")
        createServerVersion(storageDir, "3.0.0")

        assertThat(TestableInstaller(baseRoot).findCachedServer()).isNull()
    }

    @Test
    fun `findCachedServer skips directories with unparseable names`() {
        val (baseRoot, storageDir) = createLayout()
        createServerVersion(storageDir, "not-a-version")
        createServerVersion(storageDir, "1.2.0")

        val result = TestableInstaller(baseRoot).findCachedServer()
        assertThat(result?.parent?.fileName.toString()).isEqualTo("1.2.0")
    }

    @Test
    fun `cleanupOldVersions keeps current version and one fallback`() {
        val (baseRoot, storageDir) = createLayout()
        createServerVersion(storageDir, "1.0.0")
        createServerVersion(storageDir, "1.2.0")
        createServerVersion(storageDir, "1.4.0")

        TestableInstaller(baseRoot).cleanupOldVersions("1.4.0")

        assertThat(Files.exists(storageDir.resolve("1.4.0"))).isTrue()
        assertThat(Files.exists(storageDir.resolve("1.2.0"))).isTrue()
        assertThat(Files.exists(storageDir.resolve("1.0.0"))).isFalse()
    }

    @Test
    fun `cleanupOldVersions keeps highest other version as fallback`() {
        val (baseRoot, storageDir) = createLayout()
        createServerVersion(storageDir, "1.2.0")
        createServerVersion(storageDir, "1.4.0")
        createServerVersion(storageDir, "2.0.0")

        TestableInstaller(baseRoot).cleanupOldVersions("1.4.0")

        assertThat(Files.exists(storageDir.resolve("1.4.0"))).isTrue()
        // 2.0.0 is outside the <2.0.0 range, so it's not a valid fallback
        // The only valid fallback would be within range
        // But since 2.0.0 won't pass validateAllRequiredFiles (range check), it's cleaned
        assertThat(Files.exists(storageDir.resolve("1.2.0"))).isTrue()
    }

    @Test
    fun `cleanupOldVersions preserves only current when no valid fallback`() {
        val (baseRoot, storageDir) = createLayout()
        createServerVersion(storageDir, "1.4.0")

        TestableInstaller(baseRoot).cleanupOldVersions("1.4.0")

        assertThat(Files.exists(storageDir.resolve("1.4.0"))).isTrue()
    }

    @Test
    fun `cleanupOldVersions handles non-existent storage dir gracefully`() {
        val baseRoot = tempFolder.root.toPath().resolve("nonexistent")
        TestableInstaller(baseRoot).cleanupOldVersions("1.0.0")
    }

    @Test
    fun `cleanupOldVersions picks highest semver as fallback`() {
        val (baseRoot, storageDir) = createLayout()
        createServerVersion(storageDir, "1.0.0")
        createServerVersion(storageDir, "1.1.0")
        createServerVersion(storageDir, "1.2.0")
        createServerVersion(storageDir, "1.3.0")
        createServerVersion(storageDir, "1.4.0")

        TestableInstaller(baseRoot).cleanupOldVersions("1.4.0")

        assertThat(Files.exists(storageDir.resolve("1.4.0"))).isTrue()
        assertThat(Files.exists(storageDir.resolve("1.3.0"))).isTrue()
        assertThat(Files.exists(storageDir.resolve("1.2.0"))).isFalse()
        assertThat(Files.exists(storageDir.resolve("1.1.0"))).isFalse()
        assertThat(Files.exists(storageDir.resolve("1.0.0"))).isFalse()
    }

    @Test
    fun `cleanupOldVersions removes unparseable directory names`() {
        val (baseRoot, storageDir) = createLayout()
        createServerVersion(storageDir, "1.4.0")
        createServerVersion(storageDir, "temp-download")

        TestableInstaller(baseRoot).cleanupOldVersions("1.4.0")

        assertThat(Files.exists(storageDir.resolve("1.4.0"))).isTrue()
        assertThat(Files.exists(storageDir.resolve("temp-download"))).isFalse()
    }

    @Test
    fun `invalidateResolvedInstallation deletes resolved directory`() {
        val (baseRoot, storageDir) = createLayout()
        createServerVersion(storageDir, "1.4.0")

        val installer = TestableInstaller(baseRoot)
        installer.findCachedServer()
        assertThat(installer.resolvedDir).isNotNull

        installer.invalidateResolvedInstallation()

        assertThat(installer.resolvedDir).isNull()
        assertThat(Files.exists(storageDir.resolve("1.4.0"))).isFalse()
    }

    @Test
    fun `invalidateResolvedInstallation is safe to call when nothing resolved`() {
        val baseRoot = tempFolder.root.toPath().resolve("nonexistent")
        TestableInstaller(baseRoot).invalidateResolvedInstallation()
    }

    /** Creates the base directory layout: baseRoot/language-servers/cfn */
    private fun createLayout(): Pair<Path, Path> {
        val baseRoot = tempFolder.newFolder("base-${System.nanoTime()}").toPath()
        val storageDir = baseRoot.resolve("language-servers").resolve(lspName)
        Files.createDirectories(storageDir)
        return baseRoot to storageDir
    }

    private fun createServerVersion(storageDir: Path, version: String) {
        val dir = storageDir.resolve(version)
        Files.createDirectories(dir)
        Files.createFile(dir.resolve("SomeFile"))
    }
}

/**
 * Testable installer with no network access (injectable no-op fetcher and sleeper).
 */
private class TestableInstaller(baseRoot: Path) :
    BaseLspInstaller(
        config = testCfnConfig(baseRoot),
        manifestAdapter = NoOpManifestAdapter,
        httpGetText = { error("network not expected in test: $it") },
        httpGetBytes = { error("network not expected in test: $it") },
        sleep = {},
    ) {
    val resolvedDir: Path?
        get() = resolvedVersionDir.get()

    public override fun postInstall(versionDir: Path) {}
}

private fun testCfnConfig(baseRoot: Path) = LspInstallerConfig(
    name = "cfn",
    supportedVersionRange = SemVerParser.parseRange("<2.0.0"),
    manifestUrl = "https://example.com/manifest.json",
    serverFilename = "SomeFile",
    storageDir = baseRoot.resolve("language-servers").resolve("cfn"),
)

private object NoOpManifestAdapter : ManifestAdapter {
    override fun parseManifest(json: String): LspManifest = error("Not expected in test")
}
