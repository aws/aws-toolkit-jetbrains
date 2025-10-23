// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonqDoc

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

data class Button(
    val id: String,
    val text: String,
    val description: String? = null,
    val icon: String? = null,
    val keepCardAfterClick: Boolean? = false,
    val disabled: Boolean? = false,
    val waitMandatoryFormItems: Boolean? = false,
    val position: String = "inside",
    val status: String = "primary",
)

data class ProgressField(
    val title: String? = null,
    val value: Int? = null,
    val valueText: String? = null,
    val status: String? = null,
    val actions: List<Button>? = null,
    val text: String? = null,
)
