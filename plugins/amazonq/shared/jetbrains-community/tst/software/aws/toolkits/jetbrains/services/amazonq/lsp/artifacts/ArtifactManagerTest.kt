// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonq.lsp.artifacts

import com.intellij.openapi.project.Project
import com.intellij.util.text.SemVer
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.spyk
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.jetbrains.annotations.TestOnly
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import software.aws.toolkits.jetbrains.services.amazonq.lsp.artifacts.ArtifactManager.SupportedManifestVersionRange
import software.aws.toolkits.jetbrains.services.amazonq.project.manifest.ManifestManager
import java.nio.file.Path

@TestOnly
class ArtifactManagerTest {

    @TempDir
    lateinit var tempDir: Path

    private lateinit var artifactHelper: ArtifactHelper
    private lateinit var artifactManager: ArtifactManager
    private lateinit var manifestFetcher: ManifestFetcher
    private lateinit var manifestVersionRanges: SupportedManifestVersionRange
    private lateinit var mockProject: Project

    @BeforeEach
    fun setUp() {
        artifactHelper = spyk(ArtifactHelper(tempDir, 3))
        manifestFetcher = spyk(ManifestFetcher())
        manifestVersionRanges = SupportedManifestVersionRange(
            startVersion = SemVer("1.0.0", 1, 0, 0),
            endVersion = SemVer("2.0.0", 2, 0, 0)
        )
        mockProject = mockk<Project>(relaxed = true) {
            every { basePath } returns tempDir.toString()
            every { name } returns "TestProject"
        }
        artifactManager = ArtifactManager(mockProject, manifestFetcher, artifactHelper, manifestVersionRanges)
    }

    @Test
    fun `fetch artifact fetcher throws exception if manifest is null`() {
        every { manifestFetcher.fetch() }.returns(null)

        assertThatThrownBy {
            runBlocking { artifactManager.fetchArtifact() }
        }
            .isInstanceOf(LspException::class.java)
            .hasFieldOrPropertyWithValue("errorCode", LspException.ErrorCode.MANIFEST_FETCH_FAILED)
    }

    @Test
    fun `fetch artifact does not have any valid lsp versions`() {
        every { manifestFetcher.fetch() }.returns(ManifestManager.Manifest())
        artifactManager = spyk(ArtifactManager(mockProject, manifestFetcher, artifactHelper, manifestVersionRanges))

        every { artifactManager.getLSPVersionsFromManifestWithSpecifiedRange(any()) }.returns(
            ArtifactManager.LSPVersions(deListedVersions = emptyList(), inRangeVersions = emptyList())
        )

        assertThatThrownBy {
            runBlocking { artifactManager.fetchArtifact() }
        }
            .isInstanceOf(LspException::class.java)
            .hasFieldOrPropertyWithValue("errorCode", LspException.ErrorCode.NO_COMPATIBLE_LSP_VERSION)
    }

    @Test
    fun `fetch artifact if inRangeVersions are not available should fallback to local lsp`() {
        val expectedResult = listOf(Pair(tempDir, SemVer("1.0.0", 1, 0, 0)))

        every { manifestFetcher.fetch() }.returns(ManifestManager.Manifest())
        every { artifactHelper.getAllLocalLspArtifactsWithinManifestRange(any()) }.returns(expectedResult)

        runBlocking { artifactManager.fetchArtifact() }

        verify(exactly = 1) { manifestFetcher.fetch() }
        verify(exactly = 1) { artifactHelper.getAllLocalLspArtifactsWithinManifestRange(any()) }
    }

    @Test
    fun `fetch artifact have valid version in local system`() {
        val target = ManifestManager.VersionTarget(platform = "temp", arch = "temp")
        val versions = listOf(ManifestManager.Version("1.0.0", targets = listOf(target)))

        artifactManager = spyk(ArtifactManager(mockProject, manifestFetcher, artifactHelper, manifestVersionRanges))

        every { artifactManager.getLSPVersionsFromManifestWithSpecifiedRange(any()) }.returns(
            ArtifactManager.LSPVersions(deListedVersions = emptyList(), inRangeVersions = versions)
        )
        every { manifestFetcher.fetch() }.returns(ManifestManager.Manifest())

        mockkStatic("software.aws.toolkits.jetbrains.services.amazonq.lsp.artifacts.LspUtilsKt")
        every { getCurrentOS() }.returns("temp")
        every { getCurrentArchitecture() }.returns("temp")

        every { artifactHelper.getExistingLspArtifacts(any(), any()) }.returns(false)
        coEvery { artifactHelper.tryDownloadLspArtifacts(any(), any(), any()) } returns tempDir
        every { artifactHelper.deleteOlderLspArtifacts(any()) } just Runs

        runBlocking { artifactManager.fetchArtifact() }

        verify(exactly = 1) { runBlocking { artifactHelper.tryDownloadLspArtifacts(any(), any(), any()) } }
        verify(exactly = 1) { artifactHelper.deleteOlderLspArtifacts(any()) }
    }

    @Test
    fun `fetch artifact does not have valid version in local system`() {
        val target = ManifestManager.VersionTarget(platform = "temp", arch = "temp")
        val versions = listOf(ManifestManager.Version("1.0.0", targets = listOf(target)))
        val expectedResult = listOf(Pair(tempDir, SemVer("1.0.0", 1, 0, 0)))

        artifactManager = spyk(ArtifactManager(mockProject, manifestFetcher, artifactHelper, manifestVersionRanges))

        every { artifactManager.getLSPVersionsFromManifestWithSpecifiedRange(any()) }.returns(
            ArtifactManager.LSPVersions(deListedVersions = emptyList(), inRangeVersions = versions)
        )
        every { manifestFetcher.fetch() }.returns(ManifestManager.Manifest())

        mockkStatic("software.aws.toolkits.jetbrains.services.amazonq.lsp.artifacts.LspUtilsKt")
        every { getCurrentOS() }.returns("temp")
        every { getCurrentArchitecture() }.returns("temp")

        every { artifactHelper.getExistingLspArtifacts(any(), any()) }.returns(true)
        every { artifactHelper.deleteOlderLspArtifacts(any()) } just Runs
        every { artifactHelper.getAllLocalLspArtifactsWithinManifestRange(any()) }.returns(expectedResult)

        runBlocking { artifactManager.fetchArtifact() }

        verify(exactly = 0) { runBlocking { artifactHelper.tryDownloadLspArtifacts(any(), any(), any()) } }
        verify(exactly = 1) { artifactHelper.deleteOlderLspArtifacts(any()) }
    }
}
