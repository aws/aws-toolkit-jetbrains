// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonq.lsp.artifacts

import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.extensions.PluginId
import com.intellij.testFramework.ProjectExtension
import com.intellij.util.text.SemVer
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockkStatic
import io.mockk.spyk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.RegisterExtension
import org.junit.jupiter.api.io.TempDir
import software.aws.toolkits.jetbrains.services.amazonq.lsp.artifacts.ArtifactManager.SupportedManifestVersionRange
import java.nio.file.Path

class ArtifactManagerTest {
    companion object {
        @JvmField
        @RegisterExtension
        val projectExtension = ProjectExtension()
    }

    @TempDir
    lateinit var tempDir: Path

    private lateinit var artifactHelper: ArtifactHelper
    private lateinit var artifactManager: ArtifactManager
    private lateinit var manifestFetcher: ManifestFetcher
    private lateinit var manifestVersionRanges: SupportedManifestVersionRange

    @BeforeEach
    fun setUp() {
        artifactHelper = spyk(ArtifactHelper(tempDir, 3))
        manifestFetcher = spyk(ManifestFetcher())
        manifestVersionRanges = SupportedManifestVersionRange(
            startVersion = SemVer("1.0.0", 1, 0, 0),
            endVersion = SemVer("2.0.0", 2, 0, 0)
        )

        artifactManager = spyk(ArtifactManager(manifestFetcher, artifactHelper))
    }

    @Test
    fun `fetch artifact fetcher returns bundled if manifest is null`() = runTest {
        every { manifestFetcher.fetch() }.returns(null)

        val exception = assertThrows<LspException> {
            artifactManager.fetchArtifact(projectExtension.project)
        }

        assertThat(artifactManager.fetchArtifact(projectExtension.project))
            .isEqualTo(
                PluginManagerCore.getPlugin(PluginId.getId("amazon.q"))?.pluginPath?.resolve("flare")
            )
    }

    @Test
    fun `fetch artifact does not have any valid lsp versions returns bundled`() = runTest {
        every { manifestFetcher.fetch() }.returns(Manifest())

        every { artifactManager.getLSPVersionsFromManifestWithSpecifiedRange(any()) }.returns(
            ArtifactManager.LSPVersions(deListedVersions = emptyList(), inRangeVersions = emptyList())
        )

        assertThat(artifactManager.fetchArtifact(projectExtension.project))
            .isEqualTo(
                PluginManagerCore.getPlugin(PluginId.getId("amazon.q"))?.pluginPath?.resolve("flare")
            )
    }

    @Test
    fun `getLSPVersionsFromManifestWithSpecifiedRange excludes end major version`() = runTest {
        val newManifest = Manifest(versions = listOf(Version(serverVersion = "2.0.0")))
        val result = artifactManager.getLSPVersionsFromManifestWithSpecifiedRange(newManifest)
        assertThat(result.inRangeVersions).isEmpty()
    }

    @Test
    fun `fetch artifact if inRangeVersions are not available should fallback to local lsp`() = runTest {
        val expectedResult = listOf(Pair(tempDir, SemVer("1.0.0", 1, 0, 0)))

        every { manifestFetcher.fetch() }.returns(Manifest())
        every { artifactHelper.getAllLocalLspArtifactsWithinManifestRange(any()) }.returns(expectedResult)

        artifactManager.fetchArtifact(projectExtension.project)

        verify(exactly = 1) { manifestFetcher.fetch() }
        verify(exactly = 1) { artifactHelper.getAllLocalLspArtifactsWithinManifestRange(any()) }
    }

    @Test
    fun `fetch artifact have valid version in local system`() = runTest {
        val target = VersionTarget(platform = "temp", arch = "temp")
        val versions = listOf(Version("1.0.0", targets = listOf(target)))

        every { artifactManager.getLSPVersionsFromManifestWithSpecifiedRange(any()) }.returns(
            ArtifactManager.LSPVersions(deListedVersions = emptyList(), inRangeVersions = versions)
        )
        every { manifestFetcher.fetch() }.returns(Manifest())

        mockkStatic("software.aws.toolkits.jetbrains.services.amazonq.lsp.artifacts.LspUtilsKt")
        every { getCurrentOS() }.returns("temp")
        every { getCurrentArchitecture() }.returns("temp")

        every { artifactHelper.getExistingLspArtifacts(any(), any()) }.returns(false)
        coEvery { artifactHelper.tryDownloadLspArtifacts(any(), any(), any()) } returns tempDir
        every { artifactHelper.deleteOlderLspArtifacts(any()) } just Runs

        artifactManager.fetchArtifact(projectExtension.project)

        coVerify(exactly = 1) { artifactHelper.tryDownloadLspArtifacts(any(), any(), any()) }
        verify(exactly = 1) { artifactHelper.deleteOlderLspArtifacts(any()) }
    }

    @Test
    fun `fetch artifact does not have valid version in local system`() = runTest {
        val target = VersionTarget(platform = "temp", arch = "temp")
        val versions = listOf(Version("1.0.0", targets = listOf(target)))
        val expectedResult = listOf(Pair(tempDir, SemVer("1.0.0", 1, 0, 0)))

        every { artifactManager.getLSPVersionsFromManifestWithSpecifiedRange(any()) }.returns(
            ArtifactManager.LSPVersions(deListedVersions = emptyList(), inRangeVersions = versions)
        )
        every { manifestFetcher.fetch() }.returns(Manifest())

        mockkStatic("software.aws.toolkits.jetbrains.services.amazonq.lsp.artifacts.LspUtilsKt")
        every { getCurrentOS() }.returns("temp")
        every { getCurrentArchitecture() }.returns("temp")

        every { artifactHelper.getExistingLspArtifacts(any(), any()) }.returns(true)
        every { artifactHelper.deleteOlderLspArtifacts(any()) } just Runs
        every { artifactHelper.getAllLocalLspArtifactsWithinManifestRange(any()) }.returns(expectedResult)

        artifactManager.fetchArtifact(projectExtension.project)

        coVerify(exactly = 0) { artifactHelper.tryDownloadLspArtifacts(any(), any(), any()) }
        verify(exactly = 1) { artifactHelper.deleteOlderLspArtifacts(any()) }
    }
}
