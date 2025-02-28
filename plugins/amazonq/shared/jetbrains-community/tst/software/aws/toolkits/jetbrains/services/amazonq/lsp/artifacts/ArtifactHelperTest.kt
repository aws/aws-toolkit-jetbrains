// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonq.lsp.artifacts

import com.intellij.util.io.createDirectories
import com.intellij.util.text.SemVer
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockkStatic
import io.mockk.spyk
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.jetbrains.annotations.TestOnly
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.kotlin.mock
import software.aws.toolkits.jetbrains.services.amazonq.lsp.artifacts.ArtifactManager.SupportedManifestVersionRange
import software.aws.toolkits.jetbrains.services.amazonq.project.manifest.ManifestManager
import java.nio.file.Path

@TestOnly
class ArtifactHelperTest {
    @TempDir
    lateinit var tempDir: Path

    private lateinit var artifactHelper: ArtifactHelper
    private lateinit var manifestVersionRanges: SupportedManifestVersionRange
    private lateinit var mockManifestManager: ManifestManager
    private lateinit var contents: List<ManifestManager.TargetContent>

    @BeforeEach
    fun setUp() {
        artifactHelper = ArtifactHelper(tempDir, 3)
        mockManifestManager = mock()
        contents = listOf(
            ManifestManager.TargetContent(
                filename = "server.zip",
                hashes = listOf("sha384:1234")
            )
        )
    }

    @Test
    fun `removeDelistedVersions removes specified versions`() {
        val version1Dir = tempDir.resolve("1.0.0").apply { toFile().mkdirs() }
        val version2Dir = tempDir.resolve("2.0.0").apply { toFile().mkdirs() }

        val delistedVersions = listOf(
            ManifestManager.Version(serverVersion = "1.0.0")
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
    fun `getExistingLspArtifacts should find all the artifacts`() {
        val version1Dir = tempDir.resolve("1.0.0").apply { toFile().mkdirs() }

        val serverZipPath = version1Dir.resolve("server.zip")
        serverZipPath.parent.toFile().mkdirs()
        serverZipPath.toFile().createNewFile()

        val versions = listOf(
            ManifestManager.Version(serverVersion = "1.0.0")
        )

        val target = ManifestManager.VersionTarget(contents = contents)

        mockkStatic("software.aws.toolkits.jetbrains.services.amazonq.lsp.artifacts.LspUtilsKt")
        every { generateSHA384Hash(any()) } returns "1234"

        val result = artifactHelper.getExistingLspArtifacts(versions, target)

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

        val versions = listOf(
            ManifestManager.Version(serverVersion = "1.0.0")
        )

        val target = ManifestManager.VersionTarget(contents = contents)

        mockkStatic("software.aws.toolkits.jetbrains.services.amazonq.lsp.artifacts.LspUtilsKt")
        every { generateSHA384Hash(any()) } returns "1235"

        val result = artifactHelper.getExistingLspArtifacts(versions, target)

        assertThat(result).isFalse()
        assertThat(serverZipPath.toFile().exists()).isFalse()
    }

    @Test
    fun `getExistingLspArtifacts should return false if versions are empty`() {
        val versions = emptyList<ManifestManager.Version>()
        assertThat(artifactHelper.getExistingLspArtifacts(versions, null)).isFalse()
    }

    @Test
    fun `getExistingLspArtifacts should return false if target does not have contents`() {
        val versions = listOf(
            ManifestManager.Version(serverVersion = "1.0.0")
        )
        assertThat(artifactHelper.getExistingLspArtifacts(versions, null)).isFalse()
    }

    @Test
    fun `getExistingLspArtifacts should return false if Lsp path does not exist`() {
        val versions = listOf(
            ManifestManager.Version(serverVersion = "1.0.0")
        )
        assertThat(artifactHelper.getExistingLspArtifacts(versions, null)).isFalse()
    }

    @Test
    fun `tryDownloadLspArtifacts should not download artifacts if target does not have contents`() {
        val versions = listOf(ManifestManager.Version(serverVersion = "2.0.0"))
        assertThatThrownBy {
            artifactHelper.tryDownloadLspArtifacts(versions, null)
        }
            .isInstanceOf(LspException::class.java)
            .hasFieldOrPropertyWithValue("errorCode", LspException.ErrorCode.DOWNLOAD_FAILED)
        assertThat(tempDir.resolve("2.0.0").toFile().exists()).isFalse()
    }

    @Test
    fun `tryDownloadLspArtifacts should throw error if failed to download`() {
        val versions = listOf(ManifestManager.Version(serverVersion = "1.0.0"))

        val spyArtifactHelper = spyk(artifactHelper)
        every { spyArtifactHelper.downloadLspArtifacts(any(), any()) } returns false

        assertThatThrownBy {
            spyArtifactHelper.tryDownloadLspArtifacts(versions, null)
        }
            .isInstanceOf(LspException::class.java)
            .hasFieldOrPropertyWithValue("errorCode", LspException.ErrorCode.DOWNLOAD_FAILED)
    }

    @Test
    fun `tryDownloadLspArtifacts should not throw error on successful download`() {
        val versions = listOf(ManifestManager.Version(serverVersion = "1.0.0"))
        val target = ManifestManager.VersionTarget(contents = contents)
        val spyArtifactHelper = spyk(artifactHelper)

        every { spyArtifactHelper.downloadLspArtifacts(any(), any()) } returns true
        mockkStatic("software.aws.toolkits.jetbrains.services.amazonq.lsp.artifacts.LspUtilsKt")
        every { moveFilesFromSourceToDestination(any(), any()) } just Runs
        every { extractZipFile(any(), any()) } just Runs

        spyArtifactHelper.tryDownloadLspArtifacts(versions, target)
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
