// Copyright 2021 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.ecs.exec

import com.intellij.openapi.ui.DialogWrapper
import software.aws.toolkits.jetbrains.settings.EcsExecCommandSettings
import software.aws.toolkits.resources.message

class ExecuteCommandWarningDoNotShow : DialogWrapper.DoNotAskOption {
    private val settings = EcsExecCommandSettings.getInstance()
    override fun isToBeShown(): Boolean = true

    override fun setToBeShown(toBeShown: Boolean, exitCode: Int) {
        if (!toBeShown && exitCode == 0) {
            settings.showExecuteCommandWarning = false
        }
    }

    override fun canBeHidden(): Boolean = true

    override fun shouldSaveOptionsOnCancel(): Boolean = false

    override fun getDoNotShowMessage(): String = message("notice.suppress")
}
