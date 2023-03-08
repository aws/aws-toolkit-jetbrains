// Copyright 2023 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0
package software.aws.toolkits.jetbrains.services.caws

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.ListPopup
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.StatusBarWidgetFactory
import com.intellij.openapi.wm.impl.status.EditorBasedWidget
import com.intellij.util.Consumer
import java.awt.event.MouseEvent

private const val WIDGET_ID = "CawsSpaceProjectInfo"

class CawsStatusBarInstaller : StatusBarWidgetFactory {
    override fun getId(): String = WIDGET_ID

    override fun getDisplayName(): String = "spaceName/projectName"

    override fun isAvailable(project: Project): Boolean = true

    override fun createWidget(project: Project): StatusBarWidget = CawsSpaceProjectInfo(project)

    override fun disposeWidget(widget: StatusBarWidget) {
        Disposer.dispose(widget)
    }

    override fun canBeEnabledOn(statusBar: StatusBar): Boolean = true
}

private class CawsSpaceProjectInfo(project: Project) :
    StatusBarWidget,
    StatusBarWidget.MultipleTextValuesPresentation, EditorBasedWidget(project) {
    val spaceName: String? = System.getenv(CawsConstants.CAWS_ENV_ORG_NAME_VAR)
    val projectName: String? = System.getenv(CawsConstants.CAWS_ENV_PROJECT_NAME_VAR)

    override fun ID(): String = WIDGET_ID

    override fun getPresentation(): StatusBarWidget.WidgetPresentation = this

    override fun getTooltipText(): String = "$spaceName/$projectName"

    override fun getClickConsumer(): Consumer<MouseEvent>? = null

    override fun getPopupStep(): ListPopup? = null

    override fun getSelectedValue(): String {
        if (spaceName != null && projectName != null) {
            return "$spaceName/$projectName"
        }
        return ""
    }
}
