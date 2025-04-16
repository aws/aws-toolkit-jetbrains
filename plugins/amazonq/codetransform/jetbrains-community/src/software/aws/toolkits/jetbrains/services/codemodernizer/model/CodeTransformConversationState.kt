// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.codemodernizer.model

enum class CodeTransformConversationState {
    PROMPT_OBJECTIVE,
    PROMPT_TARGET_JDK_PATH,
    IDLE,
}
