// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cfnlsp.server

import com.intellij.openapi.util.SystemInfo
import software.aws.toolkit.core.utils.getLogger
import software.aws.toolkit.core.utils.warn
import software.aws.toolkits.jetbrains.core.lsp.BaseLspInstaller
import software.aws.toolkits.jetbrains.core.lsp.LspInstallerConfig
import software.aws.toolkits.jetbrains.core.lsp.ManifestAdapter
import java.nio.file.Files
import java.nio.file.Path

internal class CfnLspInstaller(
    config: LspInstallerConfig = CfnLspServerConfig.installerConfig(),
    manifestAdapter: ManifestAdapter = CfnManifestAdapter(detectCfnEnvironment()),
) : BaseLspInstaller(config, manifestAdapter) {

    override fun postInstall(versionDir: Path) {
        ensureExecutableBits(versionDir)
    }

    fun cleanupAfterResolveWithLegacy() {
        cleanupLegacyStorageDir()
        cleanupAfterResolve()
    }

    private fun ensureExecutableBits(versionDir: Path) {
        if (SystemInfo.isWindows) return

        try {
            val serverFile = findServerFileInDir(versionDir) ?: return
            val cfnInit = serverFile.parent.resolve("bin").resolve("cfn-init")
            if (Files.exists(cfnInit)) {
                cfnInit.toFile().setExecutable(true, false)
            }
        } catch (e: Exception) {
            LOG.warn(e) { "Failed to mark cfn-init executable in $versionDir" }
        }
    }

    private fun cleanupLegacyStorageDir() {
        // TODO: Do nothing for now for backwards compatibility
    }

    companion object {
        private val LOG = getLogger<CfnLspInstaller>()
    }
}
