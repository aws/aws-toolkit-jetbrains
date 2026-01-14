// Copyright 2022 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkit.jetbrains.core.credentials.pinning

import software.aws.toolkit.jetbrains.core.credentials.AwsBearerTokenConnection
import software.aws.toolkit.jetbrains.core.credentials.ToolkitConnection
import software.aws.toolkit.jetbrains.core.credentials.sono.CODECATALYST_SCOPES
import software.aws.toolkit.core.utils.debug
import software.aws.toolkit.core.utils.getLogger

class CodeCatalystConnection : FeatureWithPinnedConnection {
    override val featureId: String = "aws.codecatalyst"
    override val featureName: String = "CodeCatalyst"
    override fun supportsConnectionType(connection: ToolkitConnection): Boolean {
        if (connection !is AwsBearerTokenConnection) {
            LOG.debug { "Rejecting ${connection.id} since it's not a bearer connection" }
            return false
        }
        if (!CODECATALYST_SCOPES.all { it in connection.scopes }) {
            LOG.debug { "Rejecting ${connection.id} since it's missing a required scope" }
            return false
        }
        return true
    }

    companion object {
        private val LOG = getLogger<CodeCatalystConnection>()
        fun getInstance() = FeatureWithPinnedConnection.EP_NAME.findExtensionOrFail(CodeCatalystConnection::class.java)
    }
}
