// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonqCodeTest

const val FEATURE_NAME = "Amazon Q Unit Test Generation"

enum class ConversationState {
    IDLE,
    WAITING_FOR_BUILD_COMMAND_INPUT,
    WAITING_FOR_REGENERATE_INPUT,
    IN_PROGRESS,
}

fun generateSummaryMessage(fileName: String): String = """
    Sure. This may take a few minutes. I'll share updates here as I work on this.
    **Generating unit tests for the following methods in $fileName**:
    
""".trimIndent()
