// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.utils.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent

class SimpleAction(title: String, private val block: (AnActionEvent) -> Unit) : AnAction(title) {
    override fun actionPerformed(e: AnActionEvent) {
        block(e)
    }
}

class WrappingAction(title: String, private val action: AnAction) : AnAction(title) {
    override fun actionPerformed(e: AnActionEvent) {
        action.actionPerformed(e)
    }
}
