// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cfnlsp.server

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.module.kotlin.convertValue
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.intellij.openapi.application.ApplicationManager
import software.aws.toolkits.jetbrains.core.lsp.LspInstallerConfig
import software.aws.toolkits.jetbrains.core.lsp.LspManifest
import software.aws.toolkits.jetbrains.core.lsp.ManifestAdapter
import software.aws.toolkits.jetbrains.core.lsp.SemVerParser
import java.nio.file.Path

internal object CfnLspServerConfig {
    // Explicit release-channel override: ALPHA, BETA, or PROD (case-insensitive)
    const val ENV_ENVIRONMENT = "CFN_LSP_ENVIRONMENT"

    // Root of a local server bundle
    const val ENV_BUNDLE = "CFN_LSP_BUNDLE"

    fun installerConfig(): LspInstallerConfig = LspInstallerConfig(
        name = "cloudformation-languageserver",
        supportedVersionRange = SemVerParser.parseRange("<2.0.0"),
        manifestUrl = "https://raw.githubusercontent.com/aws-cloudformation/cloudformation-languageserver/main/assets/release-manifest.json",
        serverFilename = "cfn-lsp-server-standalone.js",
        requiredFiles = listOf("bin", "node_modules"),
        localBundleRoot = devBundleRoot(),
    )

    fun devBundleRoot(rawPath: String? = System.getenv(ENV_BUNDLE)): Path? =
        rawPath?.trim()?.takeIf { it.isNotEmpty() }?.let { Path.of(it) }
}

internal enum class CfnLspEnvironment {
    ALPHA,
    BETA,
    PROD,
}

internal fun detectCfnEnvironment(
    environmentOverride: String? = System.getenv(CfnLspServerConfig.ENV_ENVIRONMENT),
    isAutomation: Boolean = System.getenv("AWS_TOOLKIT_AUTOMATION")?.toBoolean() == true,
    isUnitTestMode: Boolean = ApplicationManager.getApplication()?.isUnitTestMode == true,
): CfnLspEnvironment {
    val normalizedOverride = environmentOverride?.trim()

    if (!normalizedOverride.isNullOrBlank()) {
        try {
            return CfnLspEnvironment.valueOf(normalizedOverride.uppercase())
        } catch (_: IllegalArgumentException) {
            // Invalid override: fall through to automatic detection.
        }
    }

    return when {
        isAutomation || isUnitTestMode -> CfnLspEnvironment.BETA
        else -> CfnLspEnvironment.PROD
    }
}

internal class CfnManifestAdapter(private val environment: CfnLspEnvironment) : ManifestAdapter {
    private val mapper = jacksonObjectMapper()
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

    override fun parseManifest(json: String): LspManifest {
        val root = mapper.readTree(json)
        val environmentKey = environment.name.lowercase()
        val channelVersions = root.get(environmentKey)

        val versionsNode = when {
            channelVersions?.isArray == true -> channelVersions
            else -> throw IllegalArgumentException("Manifest contains no versions for environment '$environmentKey'")
        }

        return LspManifest(mapper.convertValue(versionsNode))
    }
}
