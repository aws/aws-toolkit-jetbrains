// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.common.session

import software.aws.toolkits.jetbrains.services.amazonqDoc.session.SessionStateInteraction
import software.aws.toolkits.jetbrains.services.amazonqFeatureDev.session.SessionStateAction
import software.aws.toolkits.jetbrains.services.amazonqFeatureDev.session.SessionStatePhase
import software.aws.toolkits.jetbrains.services.amazonqFeatureDev.util.CancellationTokenSource

interface SessionState {
    val tabID: String
    val phase: SessionStatePhase?
    var token: CancellationTokenSource?
    var approach: String
    suspend fun interact(action: SessionStateAction): SessionStateInteraction<SessionState>
}
