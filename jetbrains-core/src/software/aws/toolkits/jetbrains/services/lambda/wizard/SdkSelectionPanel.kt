// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.lambda.wizard

import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.ErrorLabel
import com.intellij.ui.components.panels.Wrapper
import com.intellij.ui.layout.panel
import software.amazon.awssdk.services.lambda.model.Runtime
import software.aws.toolkits.jetbrains.services.lambda.runtimeGroup
import javax.swing.JComponent
import javax.swing.JLabel

interface SdkSelector {
    fun sdkSelectionPanel(): JComponent

    fun sdkSelectionLabel(): JLabel?

    fun getSdkSettings(): SdkSettings

    // Validate the SDK selection panel, return a list of violations if any, otherwise null
    fun validateAll(): List<ValidationInfo>?
}

class SdkSelectionPanel : WizardFragment {
    private val component = Wrapper()

    override fun title(): String? = null

    override fun component(): JComponent = component

    override fun validateFragment(): ValidationInfo? = null

    override fun isApplicable(template: SamProjectTemplate?): Boolean = true

    override fun update(runtime: Runtime?, template: SamProjectTemplate?) {
        val runtimeGroup = runtime?.runtimeGroup
        if (runtimeGroup == null) {
            component.setContent(ErrorLabel("No runtime selected"))
            return
        }

        val selectionPanel = SamProjectWizard.getInstance(runtimeGroup).createSdkSelectionPanel(null)
        component.setContent(
            panel {
                selectionPanel?.let {
                    row(it.sdkSelectionLabel()) {
                        it.sdkSelectionPanel()(grow)
                    }
                }
            }
        )
    }
}
