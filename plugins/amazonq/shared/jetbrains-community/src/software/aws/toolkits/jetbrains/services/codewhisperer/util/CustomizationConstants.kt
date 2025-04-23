// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.codewhisperer.util

import software.amazon.awssdk.services.codewhispererruntime.model.AccessDeniedException
import software.amazon.awssdk.services.codewhispererruntime.model.CodeWhispererRuntimeException

object CustomizationConstants {
    private const val noAccessToCustomizationMessage = "Your account is not authorized to use CodeWhisperer Enterprise."
    private const val invalidCustomizationMessage = "You are not authorized to access"

    val noAccessToCustomizationExceptionPredicate: (e: Exception) -> Boolean = { e ->
        if (e !is CodeWhispererRuntimeException) {
            false
        } else {
            e is software.amazon.awssdk.services.codewhispererruntime.model.AccessDeniedException && (e.message?.contains(noAccessToCustomizationMessage, ignoreCase = true) ?: false)
        }
    }

    val invalidCustomizationExceptionPredicate: (e: Exception) -> Boolean = { e ->
        if (e !is CodeWhispererRuntimeException) {
            false
        } else {
            e is AccessDeniedException && (e.message?.contains(invalidCustomizationMessage, ignoreCase = true) ?: false)
        }
    }
}
