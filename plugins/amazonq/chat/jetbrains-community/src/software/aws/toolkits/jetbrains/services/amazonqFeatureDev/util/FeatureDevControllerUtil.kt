// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonqFeatureDev.util

import software.aws.toolkits.jetbrains.services.amazonqFeatureDev.messages.FollowUp
import software.aws.toolkits.jetbrains.services.amazonqFeatureDev.messages.FollowUpIcons
import software.aws.toolkits.jetbrains.services.amazonqFeatureDev.messages.FollowUpStatusType
import software.aws.toolkits.jetbrains.services.amazonqFeatureDev.messages.FollowUpTypes
import software.aws.toolkits.jetbrains.services.amazonqFeatureDev.session.SessionStatePhase
import software.aws.toolkits.resources.message

enum class InsertAction {
    ALL,
    REMAINING,
    CONTINUE,
    AUTO_CONTINUE,
}

fun getFollowUpOptions(phase: SessionStatePhase?, type: InsertAction): List<FollowUp> {
    when (phase) {
        SessionStatePhase.CODEGEN -> {
            return listOf(
                FollowUp(
                    pillText = when (type) {
                        InsertAction.ALL -> message("amazonqFeatureDev.follow_up.insert_all_code")
                        InsertAction.REMAINING -> message("amazonqFeatureDev.follow_up.insert_remaining_code")
                        InsertAction.CONTINUE -> message("amazonqFeatureDev.follow_up.continue")
                        InsertAction.AUTO_CONTINUE -> message("amazonqFeatureDev.follow_up.continue")
                    },
                    type = FollowUpTypes.INSERT_CODE,
                    icon = FollowUpIcons.Ok,
                    status = FollowUpStatusType.Success
                ),
                FollowUp(
                    pillText = message("amazonqFeatureDev.follow_up.provide_feedback_and_regenerate"),
                    type = FollowUpTypes.PROVIDE_FEEDBACK_AND_REGENERATE_CODE,
                    icon = FollowUpIcons.Refresh,
                    status = FollowUpStatusType.Info
                )
            )
        }
        else -> return emptyList()
    }
}
