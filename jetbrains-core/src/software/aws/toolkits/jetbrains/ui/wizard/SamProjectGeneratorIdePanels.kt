// Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.ui.wizard

import com.intellij.openapi.ui.ValidationInfo
import software.amazon.awssdk.services.lambda.model.Runtime
import software.aws.toolkits.jetbrains.services.lambda.SamProject
import software.aws.toolkits.jetbrains.services.lambda.runtimeGroup
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel

interface SdkSelectionPanel {
    val sdkSelectionPanel: JComponent

    val sdkSelectionLabel: JLabel?

    fun registerListeners()

    fun ensureSdk()

    fun validateAll(): List<ValidationInfo>?

    companion object {
        @JvmStatic
        fun create(runtime: Runtime, generator: SamProjectGenerator): SdkSelectionPanel =
            runtime.runtimeGroup?.let {
                SamProject.getInstanceOrThrow(it).createSdkSelectionPanel(generator)
            } ?: NoOpSdkSelectionPanel()
    }
}

abstract class SdkSelectionPanelBase : SdkSelectionPanel {
    override fun registerListeners() {}

    override fun ensureSdk() {}

    override fun validateAll(): List<ValidationInfo>? = null
}

class NoOpSdkSelectionPanel : SdkSelectionPanelBase() {
    override val sdkSelectionPanel: JComponent = JPanel()

    override val sdkSelectionLabel: JLabel? = null
}