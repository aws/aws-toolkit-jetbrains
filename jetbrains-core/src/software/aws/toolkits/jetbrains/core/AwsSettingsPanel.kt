// Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.core

import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.ListPopup
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.StatusBarWidgetProvider
import com.intellij.util.Consumer
import software.aws.toolkits.core.credentials.CredentialProviderNotFound
import software.aws.toolkits.jetbrains.components.telemetry.AnActionWrapper
import software.aws.toolkits.jetbrains.core.credentials.ChangeAccountSettingsActionGroup
import software.aws.toolkits.jetbrains.core.credentials.ProjectAccountSettingsManager
import software.aws.toolkits.resources.message
import java.awt.Component
import java.awt.event.MouseEvent

class AwsSettingsPanelInstaller : StatusBarWidgetProvider {
    override fun getWidget(project: Project): StatusBarWidget = AwsSettingsPanel(project)
}

private class AwsSettingsPanel(private val project: Project) : StatusBarWidget,
    StatusBarWidget.MultipleTextValuesPresentation {
    private val accountSettingsManager = ProjectAccountSettingsManager.getInstance(project)
    private val settingsSelector = SettingsSelector(project)
    private lateinit var statusBar: StatusBar

    @Suppress("FunctionName")
    override fun ID(): String = "AwsSettingsPanel"

    override fun getTooltipText() = SettingsSelector.tooltipText

    override fun getSelectedValue(): String {
        val statusLine = try {
            val displayName = accountSettingsManager.activeCredentialProvider.displayName
            "$displayName@${accountSettingsManager.activeRegion.name}"
        } catch (_: CredentialProviderNotFound) {
            // TODO: Need to better handle the case where they have no valid profile selected
            message("settings.credentials.none_selected")
        }

        return "AWS: $statusLine"
    }

    override fun getPopupStep() = settingsSelector.settingsPopup(statusBar.component)

    override fun getClickConsumer(): Consumer<MouseEvent>? = null

    override fun getPresentation(type: StatusBarWidget.PlatformType) = this

    override fun install(statusBar: StatusBar) {
        this.statusBar = statusBar
//        project.messageBus.connect().subscribe(ProjectAccountSettingsManager.ACCOUNT_SETTINGS_CHANGED, this)
        updateWidget()
    }

//    override fun settingsChanged(event: AccountSettingsChangedNotifier.AccountSettingsEvent) {
//        updateWidget()
//    }

    private fun updateWidget() {
        statusBar.updateWidget(ID())
    }

    override fun dispose() {}
}

class SettingsSelectorAction(private val showRegions: Boolean = true) : AnActionWrapper(message("configure.toolkit")), DumbAware {
    override fun doActionPerformed(e: AnActionEvent) {
        val project = e.getRequiredData(PlatformDataKeys.PROJECT)
        val settingsSelector = SettingsSelector(project)
        settingsSelector.settingsPopup(e.dataContext, showRegions = showRegions).showCenteredInCurrentWindow(project)
    }
}

class SettingsSelector(private val project: Project) {
    fun settingsPopup(contextComponent: Component, showRegions: Boolean = true): ListPopup {
        val dataContext = DataManager.getInstance().getDataContext(contextComponent)
        return settingsPopup(dataContext, showRegions)
    }

    fun settingsPopup(dataContext: DataContext, showRegions: Boolean = true): ListPopup = JBPopupFactory.getInstance().createActionGroupPopup(
        tooltipText,
        ChangeAccountSettingsActionGroup(project, showRegions),
        dataContext,
        JBPopupFactory.ActionSelectionAid.SPEEDSEARCH,
        true,
        ActionPlaces.STATUS_BAR_PLACE
    )

    companion object {
        internal val tooltipText = message("settings.title")
    }
}
