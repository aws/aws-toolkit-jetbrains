// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonq.lsp.artifacts

import io.mockk.every
import io.mockk.spyk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import software.aws.toolkits.jetbrains.services.amazonq.project.manifest.ManifestManager

class ManifestFetcherTest {

    private lateinit var manifestFetcher: ManifestFetcher
    private lateinit var manifest: ManifestManager.Manifest

    @BeforeEach
    fun setup() {
        manifestFetcher = ManifestFetcher()
        manifest = ManifestManager.Manifest()
    }

    @Test
    fun `should return null when both local and remote manifests are null`() {
        val fetchLocalManifestMock = spyk<ManifestFetcher>(recordPrivateCalls = true)

        every { fetchLocalManifestMock["fetchManifestFromLocal"]() } returns null
        every { fetchLocalManifestMock["fetchManifestFromRemote"]() } returns null

        assertEquals(fetchLocalManifestMock.fetch(), null)
        verify { fetchLocalManifestMock["fetchManifestFromLocal"]() }
        verify { fetchLocalManifestMock["fetchManifestFromRemote"]() }
    }

    @Test
    fun `should return valid result from local should not execute remote method`() {
        val fetchLocalManifestMock = spyk<ManifestFetcher>(recordPrivateCalls = true)

        every { fetchLocalManifestMock["fetchManifestFromLocal"]() } returns manifest
        every { fetchLocalManifestMock["fetchManifestFromRemote"]() } returns null

        assertEquals(fetchLocalManifestMock.fetch(), manifest)
        verify { fetchLocalManifestMock["fetchManifestFromLocal"]() }
    }

    @Test
    fun `should return valid result from remote`() {
        val fetchLocalManifestMock = spyk<ManifestFetcher>(recordPrivateCalls = true)

        every { fetchLocalManifestMock["fetchManifestFromLocal"]() } returns null
        every { fetchLocalManifestMock["fetchManifestFromRemote"]() } returns manifest

        assertEquals(fetchLocalManifestMock.fetch(), manifest)
        verify { fetchLocalManifestMock["fetchManifestFromLocal"]() }
        verify { fetchLocalManifestMock["fetchManifestFromRemote"]() }
    }
}
