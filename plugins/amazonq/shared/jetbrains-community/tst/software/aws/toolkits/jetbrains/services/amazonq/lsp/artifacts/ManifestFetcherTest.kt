// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonq.lsp.artifacts

import com.intellij.testFramework.ApplicationExtension
import com.intellij.testFramework.utils.io.createFile
import io.mockk.every
import io.mockk.junit5.MockKExtension
import io.mockk.mockkStatic
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.io.TempDir
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.atLeastOnce
import org.mockito.kotlin.never
import org.mockito.kotlin.spy
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import software.aws.toolkits.jetbrains.core.getTextFromUrl
import software.aws.toolkits.jetbrains.services.amazonq.project.manifest.ManifestManager
import java.nio.file.Path
import java.nio.file.Paths

@ExtendWith(ApplicationExtension::class, MockitoExtension::class, MockKExtension::class)
class ManifestFetcherTest {

    private lateinit var manifestFetcher: ManifestFetcher
    private lateinit var manifest: ManifestManager.Manifest
    private lateinit var manifestManager: ManifestManager

    @BeforeEach
    fun setup() {
        manifestFetcher = spy(ManifestFetcher())
        manifestManager = spy(ManifestManager())
        manifest = ManifestManager.Manifest()
    }

    @Test
    fun `should return null when both local and remote manifests are null`() {
        whenever(manifestFetcher.fetchManifestFromLocal()).thenReturn(null)
        whenever(manifestFetcher.fetchManifestFromRemote()).thenReturn(null)

        assertThat(manifestFetcher.fetch()).isNull()
    }

    @Test
    fun `should return valid result from local should not execute remote method`() {
        whenever(manifestFetcher.fetchManifestFromLocal()).thenReturn(manifest)

        assertThat(manifestFetcher.fetch()).isNotNull().isEqualTo(manifest)
        verify(manifestFetcher, atLeastOnce()).fetchManifestFromLocal()
        verify(manifestFetcher, never()).fetchManifestFromRemote()
    }

    @Test
    fun `should return valid result from remote`() {
        whenever(manifestFetcher.fetchManifestFromLocal()).thenReturn(null)
        whenever(manifestFetcher.fetchManifestFromRemote()).thenReturn(manifest)

        assertThat(manifestFetcher.fetch()).isNotNull().isEqualTo(manifest)
        verify(manifestFetcher, atLeastOnce()).fetchManifestFromLocal()
        verify(manifestFetcher, atLeastOnce()).fetchManifestFromRemote()
    }

    @Test
    fun `fetchManifestFromRemote should return null due to invalid manifestString`() {
        mockkStatic("software.aws.toolkits.jetbrains.core.HttpUtilsKt")
        every { getTextFromUrl(any()) } returns "ManifestContent"

        assertThat(manifestFetcher.fetchManifestFromRemote()).isNull()
    }

    @Test
    fun `fetchManifestFromRemote should return manifest and update manifest`() {
        val validManifest = ManifestManager.Manifest(manifestSchemaVersion = "1.0")
        mockkStatic("software.aws.toolkits.jetbrains.core.HttpUtilsKt")

        every { getTextFromUrl(any()) } returns "{ \"manifestSchemaVersion\": \"1.0\" }"

        val result = manifestFetcher.fetchManifestFromRemote()
        assertThat(result).isNotNull().isEqualTo(validManifest)
    }

    @Test
    fun `fetchManifestFromRemote should return null if manifest is deprecated`() {
        mockkStatic("software.aws.toolkits.jetbrains.core.HttpUtilsKt")
        every { getTextFromUrl(any()) } returns
            // language=JSON
            """
            {
                "manifestSchemaVersion": "1.0",
                "isManifestDeprecated": true
            }
            """.trimIndent()

        assertThat(manifestFetcher.fetchManifestFromRemote()).isNull()
    }

    @Test
    fun `fetchManifestFromLocal should return null if path does not exist locally`() {
        whenever(manifestFetcher.lspManifestFilePath).thenReturn(Paths.get("does", "not", "exist"))
        assertThat(manifestFetcher.fetchManifestFromLocal()).isNull()
    }

    @Test
    fun `fetchManifestFromLocal should return local path if exists locally`(@TempDir tempDir: Path) {
        val manifestFile = tempDir.createFile("manifest.json")
        manifestFile.toFile().writeText(
            // language=JSON
            """ 
            {
                "manifestSchemaVersion": "1.0"
            }
            """.trimIndent()
        )
        whenever(manifestFetcher.lspManifestFilePath).thenReturn(manifestFile)
        assertThat(manifestFetcher.fetchManifestFromLocal()).isNull()
    }
}
