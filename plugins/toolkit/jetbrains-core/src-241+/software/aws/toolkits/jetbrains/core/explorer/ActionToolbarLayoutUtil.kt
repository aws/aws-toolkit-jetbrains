// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0
package software.aws.toolkits.jetbrains.core.explorer

import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.toolbarLayout.ToolbarLayoutStrategy

fun setToolbarLayout(toolbar: ActionToolbar) {
    toolbar.layoutStrategy = ToolbarLayoutStrategy.NOWRAP_STRATEGY
}
