// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonqFeatureDev.session

import software.aws.toolkits.jetbrains.services.amazonqFeatureDev.util.CancellationTokenSource

class ConversationNotStartedState(
    override var approach: String,
    override val tabID: String,
    override var token: CancellationTokenSource?,
    override var codeGenerationRemainingIterationCount: Int?,
    override var codeGenerationTotalIterationCount: Int?,
    override var currentIteration: Int?,
) : SessionState {
    override val phase = SessionStatePhase.INIT

    override suspend fun interact(action: SessionStateAction): SessionStateInteraction {
        error("Illegal transition between states, restart the conversation")
    }
}
