// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0
package software.aws.toolkits.jetbrains.core.explorer

import com.intellij.openapi.actionSystem.ActionToolbar

fun setToolbarLayout(toolbar: ActionToolbar) {
    toolbar.layoutPolicy = ActionToolbar.NOWRAP_LAYOUT_POLICY
}
