// Copyright 2022 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.amazon.q.jetbrains.core.credentials.pinning

import software.amazon.q.core.utils.debug
import software.amazon.q.core.utils.getLogger
import software.amazon.q.jetbrains.core.credentials.AwsBearerTokenConnection
import software.amazon.q.jetbrains.core.credentials.ToolkitConnection
import software.amazon.q.jetbrains.core.credentials.sono.CODECATALYST_SCOPES

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
