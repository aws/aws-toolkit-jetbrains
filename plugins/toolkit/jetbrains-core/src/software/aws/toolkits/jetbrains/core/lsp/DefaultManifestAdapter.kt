// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.core.lsp

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.module.kotlin.convertValue
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper

interface ManifestAdapter {
    fun parseManifest(json: String): LspManifest
}

data class LspManifest(val versions: List<LspManifestVersion>)

data class LspManifestVersion(
    val serverVersion: String,
    val tag: String? = null,
    val latest: Boolean? = null,
    val isDelisted: Boolean = false,
    val targets: List<LspManifestTarget>,
)

data class LspManifestTarget(val platform: String, val arch: String, val contents: List<LspContentEntry>)

data class LspContentEntry(
    val filename: String,
    val url: String,
    val hashes: List<String> = emptyList(),
    val bytes: Long,
)

internal class DefaultManifestAdapter : ManifestAdapter {
    private val mapper = jacksonObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

    override fun parseManifest(json: String): LspManifest {
        val root = mapper.readTree(json)
        val versionsNode = root.get("versions")?.takeIf { it.isArray } ?: throw IllegalArgumentException("Manifest must contain a top-level 'versions' array")

        return LspManifest(mapper.convertValue(versionsNode))
    }
}
