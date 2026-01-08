// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cfnlsp.server

import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

class CfnLspServerConfigTest {

    @Test
    fun `server config has correct values`() {
        assertThat(CfnLspServerConfig.LSP_NAME).isEqualTo("cloudformation-languageserver")
        assertThat(CfnLspServerConfig.SERVER_FILE).isEqualTo("cfn-lsp-server-standalone.js")
        assertThat(CfnLspServerConfig.GITHUB_OWNER).isEqualTo("aws-cloudformation")
        assertThat(CfnLspServerConfig.GITHUB_REPO).isEqualTo("cloudformation-languageserver")
    }

    @Test
    fun `environment enum has expected values`() {
        assertThat(CfnLspEnvironment.values()).containsExactly(
            CfnLspEnvironment.ALPHA,
            CfnLspEnvironment.BETA,
            CfnLspEnvironment.PROD
        )
    }
}
