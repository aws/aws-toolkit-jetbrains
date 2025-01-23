// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonqDoc

import software.aws.toolkits.jetbrains.services.amazonqCodeTest.messages.Button
import software.aws.toolkits.jetbrains.services.amazonqCodeTest.messages.ProgressField
import software.aws.toolkits.resources.message

val cancellingProgressField = ProgressField(
    status = "warning",
    text = message("general.canceling"),
    value = -1,
    actions = emptyList()
)

// TODO: Need to change the string after the F2F
val docGenCompletedField = ProgressField(
    status = "success",
    text = message("general.success"),
    value = 100,
    actions = emptyList()
)

val cancelTestGenButton = Button(
    id = "doc_stop_generate",
    text = message("general.cancel"),
    icon = "cancel"
)

fun inProgress(progress: Int, message: String? = null): ProgressField? {
    // Constants to improve readability and maintainability
    val completionProgress = 100
    val completionValue = -1

    // Pre-calculate the conditions to avoid repeated evaluations
    val isComplete = progress >= completionProgress

    return ProgressField(
        status = "default",
        text = message ?: message("amazonqDoc.inprogress_message.generating"),
        value = if (isComplete) completionValue else progress,
        actions = listOf(cancelTestGenButton)
    )
}
