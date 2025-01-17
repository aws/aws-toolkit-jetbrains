// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonqCodeTest

import software.aws.toolkits.jetbrains.services.amazonqCodeTest.messages.Button
import software.aws.toolkits.jetbrains.services.amazonqCodeTest.messages.CodeTestButtonId
import software.aws.toolkits.jetbrains.services.amazonqCodeTest.messages.ProgressField
import software.aws.toolkits.resources.message

val cancellingProgressField = ProgressField(
    status = "warning",
    text = message("general.canceling"),
    value = -1,
    actions = emptyList()
)

// TODO: Need to change the string after the F2F
val testGenCompletedField = ProgressField(
    status = "success",
    text = message("general.success"),
    value = 100,
    actions = emptyList()
)

val cancelTestGenButton = Button(
    id = CodeTestButtonId.StopTestGeneration.id,
    text = message("general.cancel"),
    icon = "cancel"
)

fun testGenProgressField(value: Int) = ProgressField(
    status = "default",
    text = message("testgen.progressbar.generate_unit_tests"),
    value = value,
    valueText = "$value%",
    actions = listOf(cancelTestGenButton)
)
