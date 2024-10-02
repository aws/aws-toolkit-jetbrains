// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.lambda.python

import com.intellij.ide.util.projectWizard.WizardContext
import com.intellij.ide.wizard.NewProjectWizardStep
import com.intellij.openapi.observable.properties.PropertyGraph
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.util.UserDataHolder
import com.intellij.ui.dsl.builder.panel
import com.jetbrains.python.configuration.PyConfigurableInterpreterList
import com.jetbrains.python.newProject.NewPythonProjectStep
import com.jetbrains.python.sdk.PreferredSdkComparator
import com.jetbrains.python.sdk.PythonSdkType
import com.jetbrains.python.sdk.PythonSdkUtil
import software.aws.toolkits.jetbrains.services.lambda.wizard.SdkSelector
import javax.swing.JComponent
import javax.swing.JLabel

class PyCharmSdkSelectionPanel(private val projectLocation: TextFieldWithBrowseButton?) : SdkSelector {
//    private val sdkPanel by lazy {
//        sdkPanel()
//    }


    private val sdkStep by lazy {
        NewPythonProjectStep(object : NewProjectWizardStep {
            override val context: WizardContext
                get() = TODO("Not yet implemented")
            override val propertyGraph: PropertyGraph
                get() = TODO("Not yet implemented")
            override val keywords: NewProjectWizardStep.Keywords
                get() = TODO("Not yet implemented")
            override val data: UserDataHolder
                get() = TODO("Not yet implemented")

        })
    }

    private val sdkPanel by lazy {
        panel {
            sdkStep.setupUI(this)
        }
    }

    override fun sdkSelectionPanel(): JComponent = sdkPanel

    override fun sdkSelectionLabel(): JLabel? = null

//    private fun sdkPanel(): PyAddSdkGroupPanel {
//        // Based on PyCharm's ProjectSpecificSettingsStep
//        val existingSdks = getValidPythonSdks()
//        val newProjectLocation = getProjectLocation()
//        val newEnvironmentPanel = PyAddNewEnvironmentPanel(existingSdks, newProjectLocation, null as String?)
//        val existingSdkPanel = PyAddExistingSdkPanel(null, null, existingSdks, newProjectLocation, existingSdks.firstOrNull())
//
//        val defaultPanel = if (PySdkSettings.instance.useNewEnvironmentForNewProject) newEnvironmentPanel else existingSdkPanel
//        PyAddSdkGroupPanel()
//        val interpreterPanel = createPythonSdkPanel(listOf(newEnvironmentPanel, existingSdkPanel), defaultPanel)
//
//        projectLocation?.textField?.document?.addDocumentListener(
//            object : DocumentAdapter() {
//                override fun textChanged(e: DocumentEvent) {
//                    interpreterPanel.newProjectPath = getProjectLocation()
//                }
//            }
//        )
//
//        return interpreterPanel
//    }

    private fun getProjectLocation(): String? = projectLocation?.text?.trim()

    private fun getValidPythonSdks(): List<Sdk> = PyConfigurableInterpreterList.getInstance(null).allPythonSdks
        .asSequence()
        .filter { it.sdkType is PythonSdkType && !PythonSdkUtil.isInvalid(it) }
        .sortedWith(PreferredSdkComparator())
        .toList()

    override fun getSdk(): Sdk? {
        return sdkStep.pythonSdk
    }

    override fun validateSelection(): ValidationInfo? = sdkPanel.validateAll().firstOrNull()
}
