// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonq.lsp.artifacts

import com.intellij.openapi.project.Project
import com.intellij.testFramework.ApplicationExtension
import com.intellij.util.io.createDirectories
import com.intellij.util.text.SemVer
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.spyk
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.io.TempDir
import software.aws.toolkits.jetbrains.services.amazonq.lsp.artifacts.ArtifactManager.SupportedManifestVersionRange
import java.nio.file.Path

@ExtendWith(ApplicationExtension::class)
class ArtifactHelperTest {
    @TempDir
    lateinit var tempDir: Path

    private lateinit var artifactHelper: ArtifactHelper
    private lateinit var manifestVersionRanges: SupportedManifestVersionRange
    private lateinit var contents: List<TargetContent>
    private lateinit var mockProject: Project

    @BeforeEach
    fun setUp() {
        artifactHelper = ArtifactHelper(tempDir, 3)
        contents = listOf(
            TargetContent(
                filename = "server.zip",
                hashes = listOf("sha384:1234")
            )
        )
        mockProject = mockk<Project>(relaxed = true) {
            every { basePath } returns tempDir.toString()
            every { name } returns "TestProject"
        }
    }

    @Test
    fun `removeDelistedVersions removes specified versions`() {
        val version1Dir = tempDir.resolve("1.0.0").apply { toFile().mkdirs() }
        val version2Dir = tempDir.resolve("2.0.0").apply { toFile().mkdirs() }

        val delistedVersions = listOf(
            Version(serverVersion = "1.0.0")
        )

        artifactHelper.removeDelistedVersions(delistedVersions)

        assertThat(version1Dir.toFile().exists()).isFalse()
        assertThat(version2Dir.toFile().exists()).isTrue()
    }

    @Test
    fun `deleteOlderLspArtifacts should not delete if there are only two version`() {
        val version1Dir = tempDir.resolve("1.0.0").apply { toFile().mkdirs() }
        val version2Dir = tempDir.resolve("1.0.1").apply { toFile().mkdirs() }

        manifestVersionRanges = SupportedManifestVersionRange(
            startVersion = SemVer("1.0.0", 1, 0, 0),
            endVersion = SemVer("2.0.0", 2, 0, 0)
        )

        artifactHelper.deleteOlderLspArtifacts(manifestVersionRanges)

        assertThat(version1Dir.toFile().exists()).isTrue()
        assertThat(version2Dir.toFile().exists()).isTrue()
    }

    @Test
    fun `deleteOlderLspArtifacts should delete if there are more than two versions`() {
        val version1Dir = tempDir.resolve("1.0.0").apply { toFile().mkdirs() }
        val version2Dir = tempDir.resolve("1.0.1").apply { toFile().mkdirs() }
        val version3Dir = tempDir.resolve("1.0.2").apply { toFile().mkdirs() }

        manifestVersionRanges = SupportedManifestVersionRange(
            startVersion = SemVer("1.0.0", 1, 0, 0),
            endVersion = SemVer("2.0.0", 2, 0, 0)
        )

        artifactHelper.deleteOlderLspArtifacts(manifestVersionRanges)

        assertThat(version1Dir.toFile().exists()).isFalse()
        assertThat(version2Dir.toFile().exists()).isTrue()
        assertThat(version3Dir.toFile().exists()).isTrue()
    }

    @Test
    fun `getAllLocalLspArtifactsWithinManifestRange should return matching folder path`() {
        tempDir.resolve("1.0.0").createDirectories()
        tempDir.resolve("1.0.1").createDirectories()
        tempDir.resolve("1.0.2").createDirectories()

        manifestVersionRanges = SupportedManifestVersionRange(
            startVersion = SemVer("1.0.0", 1, 0, 0),
            endVersion = SemVer("2.0.0", 2, 0, 0)
        )

        val actualResult = artifactHelper.getAllLocalLspArtifactsWithinManifestRange(manifestVersionRanges)
        assertThat(actualResult).isNotNull()
        assertThat(actualResult.size).isEqualTo(3)
        assertThat(actualResult.first().first.fileName.toString()).isEqualTo("1.0.2")
    }

    @Test
    fun `getAllLocalLspArtifactsWithinManifestRange does not return end major version path`() {
        tempDir.resolve("1.0.0").createDirectories()
        tempDir.resolve("2.0.0").createDirectories()
        manifestVersionRanges = SupportedManifestVersionRange(
            startVersion = SemVer("1.0.0", 1, 0, 0),
            endVersion = SemVer("2.0.0", 2, 0, 0)
        )

        val actualResult = artifactHelper.getAllLocalLspArtifactsWithinManifestRange(manifestVersionRanges)
        assertThat(actualResult).isNotNull()
        assertThat(actualResult.size).isEqualTo(1)
        assertThat(actualResult.first().first.fileName.toString()).isNotEqualTo("2.0.0")
    }

    @Test
    fun `getExistingLspArtifacts should find all the artifacts`() {
        val version1Dir = tempDir.resolve("1.0.0").apply { toFile().mkdirs() }

        val serverZipPath = version1Dir.resolve("server.zip")
        serverZipPath.parent.toFile().mkdirs()
        serverZipPath.toFile().createNewFile()

        val version = Version(serverVersion = "1.0.0")

        val target = VersionTarget(contents = contents)

        mockkStatic("software.aws.toolkits.jetbrains.services.amazonq.lsp.artifacts.LspUtilsKt")
        every { generateSHA384Hash(any()) } returns "1234"

        val result = artifactHelper.getExistingLspArtifacts(version, target)

        assertThat(result).isTrue()
        assertThat(serverZipPath.toFile().exists()).isTrue()
        version1Dir.toFile().deleteRecursively()
    }

    @Test
    fun `getExistingLspArtifacts should return false due to hash mismatch`() {
        val version1Dir = tempDir.resolve("1.0.0").apply { toFile().mkdirs() }

        val serverZipPath = version1Dir.resolve("server.zip")
        serverZipPath.parent.toFile().mkdirs()
        serverZipPath.toFile().createNewFile()

        val version = Version(serverVersion = "1.0.0")

        val target = VersionTarget(contents = contents)

        mockkStatic("software.aws.toolkits.jetbrains.services.amazonq.lsp.artifacts.LspUtilsKt")
        every { generateSHA384Hash(any()) } returns "1235"

        val result = artifactHelper.getExistingLspArtifacts(version, target)

        assertThat(result).isFalse()
        assertThat(serverZipPath.toFile().exists()).isFalse()
    }

    @Test
    fun `getExistingLspArtifacts should return false if target does not have contents`() {
        val version = Version(serverVersion = "1.0.0")
        val target1 = VersionTarget(contents = emptyList())
        val target2 = VersionTarget(contents = null)
        assertThat(artifactHelper.getExistingLspArtifacts(version, target1)).isFalse()
        assertThat(artifactHelper.getExistingLspArtifacts(version, target2)).isFalse()
    }

    @Test
    fun `getExistingLspArtifacts should return false if Lsp path does not exist`() {
        val versions = Version(serverVersion = "1.0.0")
        val target = VersionTarget(contents = contents)
        assertThat(artifactHelper.getExistingLspArtifacts(versions, target)).isFalse()
    }

    @Test
    fun `tryDownloadLspArtifacts should not download artifacts if target does not have contents`() {
        val versions = Version(serverVersion = "2.0.0")
        assertThat(runBlocking { artifactHelper.tryDownloadLspArtifacts(mockProject, versions, VersionTarget()) }).isEqualTo(null)
        assertThat(tempDir.resolve("2.0.0").toFile().exists()).isFalse()
    }

    @Test
    fun `tryDownloadLspArtifacts should throw error if failed to download`() {
        val version = Version(serverVersion = "1.0.0")

        val spyArtifactHelper = spyk(artifactHelper)
        every { spyArtifactHelper.downloadLspArtifacts(mockProject, any(), any()) } returns false

        assertThat(runBlocking { artifactHelper.tryDownloadLspArtifacts(mockProject, version, VersionTarget(contents = contents)) }).isEqualTo(null)
    }

    @Test
    fun `tryDownloadLspArtifacts should throw error after attempts are exhausted`() {
        val version = Version(serverVersion = "1.0.0")
        val target = VersionTarget(contents = contents)
        val spyArtifactHelper = spyk(artifactHelper)

        every { spyArtifactHelper.downloadLspArtifacts(mockProject, any(), any()) } returns true
        mockkStatic("software.aws.toolkits.jetbrains.services.amazonq.lsp.artifacts.LspUtilsKt")
        every { moveFilesFromSourceToDestination(any(), any()) } just Runs
        every { extractZipFile(any(), any()) } just Runs

        assertThat(runBlocking { artifactHelper.tryDownloadLspArtifacts(mockProject, version, target) }).isEqualTo(null)
    }

    @Test
    fun `validateFileHash should return false if expected hash is null`() {
        assertThat(artifactHelper.validateFileHash(tempDir, null)).isFalse()
    }

    @Test
    fun `validateFileHash should return false if hash did not match`() {
        mockkStatic("software.aws.toolkits.jetbrains.services.amazonq.lsp.artifacts.LspUtilsKt")
        every { generateSHA384Hash(any()) } returns "1234"
        assertThat(artifactHelper.validateFileHash(tempDir, "1234")).isFalse()
    }

    @Test
    fun `validateFileHash should return true if hash matched`() {
        mockkStatic("software.aws.toolkits.jetbrains.services.amazonq.lsp.artifacts.LspUtilsKt")
        every { generateSHA384Hash(any()) } returns "1234"
        assertThat(artifactHelper.validateFileHash(tempDir, "sha384:1234")).isTrue()
    }
}
