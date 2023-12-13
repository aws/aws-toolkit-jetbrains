// Copyright 2023 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cwc.utility

object EdtUtility {
    fun runInEdt(action: () -> Unit) {
        com.intellij.openapi.application.runInEdt { action() }
    }

    fun <T> computeOnEdt(action: () -> T) : T {
        return software.aws.toolkits.jetbrains.utils.computeOnEdt { action() }
    }

    fun <T> runReadAction(action: () -> T) : T {
        return com.intellij.openapi.application.runReadAction { action() }
    }
}
