// Copyright 2023 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.core.explorer.filters

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent

class AwsExplorerFilterClearAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        e.project?.let {
            AwsExplorerFilterManager.getInstance(it).clearFilter()
        }
    }
}
