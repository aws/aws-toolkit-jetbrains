// Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.ui.wizard

import com.intellij.ide.util.projectWizard.AbstractNewProjectStep
import com.intellij.ide.util.projectWizard.SdkSettingsStep
import com.intellij.ide.util.projectWizard.WizardContext
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.SdkTypeId
import com.intellij.openapi.util.Condition
import software.amazon.awssdk.services.lambda.model.Runtime
import software.aws.toolkits.jetbrains.services.lambda.runtimeGroup
import java.awt.event.ItemEvent
import javax.swing.JComponent
import javax.swing.JLabel

class IntelliJSdkSelectionPanel(callback: AbstractNewProjectStep.AbstractCallback<SamNewProjectSettings>, generator: SamProjectGenerator) : SdkSelectionPanelBase(callback, generator) {
    fun sdkPanelFilter(runtime: Runtime): Condition<SdkTypeId> = Condition { sdkTypeId ->
        try {
            // runtime group cannot be null since we populated the list of runtimes from the list of supported runtime groups
            val runtimeGroup = runtime.runtimeGroup
            sdkTypeId == runtimeGroup?.getIdeSdkType()
        } catch (e: NullPointerException) {
            // degrade experience instead of failing to draw the UI
            true
        }
    }

    var currentSdk: Sdk? = null

    private fun buildSdkSettingsPanel(runtime: Runtime) {
        currentSdkPanel = object : SdkSettingsStep(object : WizardContext(null, {}) {}, AwsModuleBuilder(generator), sdkPanelFilter(runtime), null) {
            override fun onSdkSelected(sdk: Sdk?) {
                currentSdk = sdk
            }
        }
    }

    private lateinit var currentSdkPanel: SdkSettingsStep
    override val sdkSelectionPanel: JComponent
        get() {
            if (!::currentSdkPanel.isInitialized) {
                buildSdkSettingsPanel(generator.settings.runtime)
            }

            return currentSdkPanel.component
        }

    override fun transformUI(panel: SamInitSelectionPanel) {
        super.transformUI(panel)

        panel.addSdkPanel(JLabel("Project SDK:"), sdkSelectionPanel)

        panel.runtime.addItemListener { l ->
            if (l.stateChange == ItemEvent.SELECTED) {
                buildSdkSettingsPanel(l.item as Runtime)
                panel.addSdkPanel(JLabel("Project SDK:"), sdkSelectionPanel)
            }
        }
    }

    override fun getSdk() = currentSdk
}