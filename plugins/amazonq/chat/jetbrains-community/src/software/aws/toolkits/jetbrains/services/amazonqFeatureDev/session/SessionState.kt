// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonqFeatureDev.session

import software.aws.toolkits.jetbrains.services.amazonqFeatureDev.util.CancellationTokenSource

interface SessionState {
    val tabID: String
    val phase: SessionStatePhase?
    var token: CancellationTokenSource?
    var codeGenerationRemainingIterationCount: Int?
    var codeGenerationTotalIterationCount: Int?
    var currentIteration: Int?
    var approach: String
    suspend fun interact(action: SessionStateAction): SessionStateInteraction
}
