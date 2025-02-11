// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonq.lsp

import software.aws.toolkits.jetbrains.services.amazonq.project.manifest.ManifestManager

class ArtifactManager {
    private val manifestManager = ManifestManager()
    private val lspManifestUrl = "https://aws-toolkit-language-servers.amazonaws.com/codewhisperer/0/manifest.json"

    fun fetchArtifact() {
        fetchManifest()
    }

    private fun fetchManifest() {
        val manifest = manifestManager.getManifest(lspManifestUrl)
        println(manifest)
    }
}
