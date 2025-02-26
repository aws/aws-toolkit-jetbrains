// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.common.session

import software.aws.toolkits.jetbrains.common.util.AmazonQCodeGenService
import software.aws.toolkits.jetbrains.services.amazonq.project.FeatureDevSessionContext

open class SessionStateConfig(
    open val conversationId: String,
    open val repoContext: FeatureDevSessionContext,
    open val amazonQCodeGenService: AmazonQCodeGenService,
)

data class SessionStateConfigData(
    override val conversationId: String,
    override val repoContext: FeatureDevSessionContext,
    override val amazonQCodeGenService: AmazonQCodeGenService,
) : SessionStateConfig(conversationId, repoContext, amazonQCodeGenService)

enum class Intent {
    DEV,
    DOC,
}
