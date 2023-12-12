// Copyright 2023 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cwc.utility
object EdtUtility {
    fun runInEdt(action: () -> Unit) {
        com.intellij.openapi.application.ApplicationManager.getApplication().invokeAndWait(action)
    }
}
