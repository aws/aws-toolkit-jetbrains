// Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.welcome

import com.intellij.ide.BrowserUtil
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent

class LearnAwsAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        try {
            BrowserUtil.browse("https://docs.aws.amazon.com/toolkit-for-jetbrains/latest/userguide/welcome.html")
        } catch (_: Exception) {
            // ignore
        }
    }
}