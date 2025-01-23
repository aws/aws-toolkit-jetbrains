// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonqDoc

import software.aws.toolkits.jetbrains.services.amazonqDoc.messages.FollowUp
import software.aws.toolkits.jetbrains.services.amazonqDoc.messages.FollowUpStatusType
import software.aws.toolkits.jetbrains.services.amazonqDoc.messages.FollowUpTypes
import software.aws.toolkits.resources.message

const val FEATURE_EVALUATION_PRODUCT_NAME = "DocGeneration"

const val FEATURE_NAME = "Amazon Q Documentation Generation"

// Max number of times a user can attempt to retry a code generation request if it fails
const val CODE_GENERATION_RETRY_LIMIT = 3

// The default retry limit used when the session could not be found
const val DEFAULT_RETRY_LIMIT = 0

// Max allowed size for a repository in bytes
const val MAX_PROJECT_SIZE_BYTES: Long = 200 * 1024 * 1024

enum class ModifySourceFolderErrorReason(
    private val reasonText: String,
) {
    ClosedBeforeSelection("ClosedBeforeSelection"),
    NotInWorkspaceFolder("NotInWorkspaceFolder"),
    ;

    override fun toString(): String = reasonText
}

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
