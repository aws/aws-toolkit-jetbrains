// Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.lambda.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.util.PlatformUtils

/**
 * The action is duplicated for Rider to replace ProjectViewPopupMenu that adds action to the "Tools"
 * solution context menu with SolutionExplorerPopupMenu action group to add Deploy action to context top level
 *
 * TODO: Actions can be resolved using ActionConfigurationCustomizer when min-version is 19.3 FIX_WHEN_MIN_IS_193
 */
class RiderDeployServerlessApplicationAction : DeployServerlessApplicationAction() {

    override fun update(e: AnActionEvent) {
        super.update(e)
        e.presentation.isVisible = getSamTemplateFile(e) != null && PlatformUtils.isRider()
    }
}
