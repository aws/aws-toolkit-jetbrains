// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cfnlsp.server

internal object CfnLspServerConfig {
    const val SERVER_FILE = "cfn-lsp-server-standalone.js"
    const val GITHUB_OWNER = "aws-cloudformation"
    const val GITHUB_REPO = "cloudformation-languageserver"

    /** Semver range constraint for compatible language server versions. */
    const val SUPPORTED_VERSION_RANGE = "<2.0.0"
}

internal enum class CfnLspEnvironment {
    ALPHA, BETA, PROD
}
