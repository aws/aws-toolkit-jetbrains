// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonqDoc.ui

import software.aws.toolkits.jetbrains.services.amazonqDoc.messages.FollowUp
import software.aws.toolkits.jetbrains.services.amazonqDoc.messages.FollowUpStatusType
import software.aws.toolkits.jetbrains.services.amazonqDoc.messages.FollowUpTypes
import software.aws.toolkits.resources.message

val NEW_SESSION_FOLLOWUPS: List<FollowUp> = listOf(
    FollowUp(
        pillText = message("amazonqDoc.prompt.reject.new_task"),
        type = FollowUpTypes.NEW_TASK,
        status = FollowUpStatusType.Info
    ),
    FollowUp(
        pillText = message("amazonqDoc.prompt.reject.close_session"),
        type = FollowUpTypes.CLOSE_SESSION,
        status = FollowUpStatusType.Info
    )
)
