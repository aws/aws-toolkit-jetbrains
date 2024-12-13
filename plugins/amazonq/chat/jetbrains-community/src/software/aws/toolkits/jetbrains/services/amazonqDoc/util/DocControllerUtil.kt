// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonqDoc.util

import software.aws.toolkits.jetbrains.services.amazonqDoc.messages.FollowUp
import software.aws.toolkits.jetbrains.services.amazonqDoc.messages.FollowUpIcons
import software.aws.toolkits.jetbrains.services.amazonqDoc.messages.FollowUpStatusType
import software.aws.toolkits.jetbrains.services.amazonqDoc.messages.FollowUpTypes
import software.aws.toolkits.jetbrains.services.amazonqFeatureDev.session.SessionStatePhase
import software.aws.toolkits.resources.message

fun getFollowUpOptions(phase: SessionStatePhase?): List<FollowUp> {
    when (phase) {
        SessionStatePhase.CODEGEN -> {
            return listOf(
                FollowUp(
                    pillText = message("amazonqDoc.prompt.review.accept"),
                    prompt = message("amazonqDoc.prompt.review.accept"),
                    status = FollowUpStatusType.Success,
                    type = FollowUpTypes.ACCEPT_CHANGES,
                    icon = FollowUpIcons.Ok,
                ),
                FollowUp(
                    pillText = message("amazonqDoc.prompt.review.changes"),
                    prompt = message("amazonqDoc.prompt.review.changes"),
                    status = FollowUpStatusType.Info,
                    type = FollowUpTypes.MAKE_CHANGES,
                    icon = FollowUpIcons.Info,
                ),
                FollowUp(
                    pillText = message("general.reject"),
                    prompt = message("general.reject"),
                    status = FollowUpStatusType.Error,
                    type = FollowUpTypes.REJECT_CHANGES,
                    icon = FollowUpIcons.Cancel,
                )
            )
        }

        else -> return emptyList()
    }
}
