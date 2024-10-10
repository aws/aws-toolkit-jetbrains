// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.lambda.python

import com.intellij.ide.util.projectWizard.WizardContext
import com.intellij.ide.wizard.NewProjectWizardBaseData
import com.intellij.ide.wizard.NewProjectWizardStep
import com.intellij.openapi.observable.properties.PropertyGraph
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.ui.dsl.builder.panel
import com.jetbrains.python.newProject.NewPythonProjectStep
import software.aws.toolkits.jetbrains.services.lambda.wizard.SdkSelector
import javax.swing.JComponent
import javax.swing.JLabel

@Suppress("UnusedPrivateProperty")
class PyCharmSdkSelectionPanel(private val projectLocation: TextFieldWithBrowseButton?) : SdkSelector {
    private val sdkStep by lazy {
        val graph = PropertyGraph()
        NewPythonProjectStep(object : NewProjectWizardStep {
            override val context = WizardContext(null, null)
            override val propertyGraph: PropertyGraph = graph
            override val keywords = NewProjectWizardStep.Keywords()
            override val data = UserDataHolderBase().apply {
                putUserData(
                    NewProjectWizardBaseData.KEY,
                    object : NewProjectWizardBaseData {
                        override val nameProperty = graph.property("")
                        override val pathProperty = graph.property(projectLocation?.text?.trim() ?: "")

                        override var name by nameProperty
                        override var path by pathProperty
                    }
                )
            }
        })
    }

    private val sdkPanel by lazy {
        panel {
            sdkStep.setupUI(this)
        }
    }

    override fun sdkSelectionPanel(): JComponent = sdkPanel

    override fun sdkSelectionLabel(): JLabel? = null

    override fun applySdkSettings(model: ModifiableRootModel) {
        sdkStep.setupProject(model.project)
    }

    override fun validateSelection(): ValidationInfo? = sdkPanel.validateAll().firstOrNull()
}
