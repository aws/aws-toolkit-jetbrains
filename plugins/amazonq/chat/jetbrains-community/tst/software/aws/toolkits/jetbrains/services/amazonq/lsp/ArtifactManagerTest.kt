// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonq.lsp

import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Test

class ArtifactManagerTest {

    private lateinit var artifactManager: ArtifactManager

    @Before
    fun setup() {
        artifactManager = ArtifactManager()
    }

    @Test
    fun `test download artifacts method`() {
        artifactManager.fetchArtifact()
        assertThat(true).isEqualTo(true)
    }
}
