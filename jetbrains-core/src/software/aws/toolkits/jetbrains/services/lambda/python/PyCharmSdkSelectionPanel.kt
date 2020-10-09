// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.lambda.python

import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.ui.ValidationInfo
import com.jetbrains.python.configuration.PyConfigurableInterpreterList
import com.jetbrains.python.newProject.steps.PyAddExistingSdkPanel
import com.jetbrains.python.newProject.steps.PyAddNewEnvironmentPanel
import com.jetbrains.python.sdk.PreferredSdkComparator
import com.jetbrains.python.sdk.PySdkSettings
import com.jetbrains.python.sdk.PythonSdkType
import com.jetbrains.python.sdk.PythonSdkUtil
import com.jetbrains.python.sdk.add.PyAddSdkGroupPanel
import software.aws.toolkits.jetbrains.services.lambda.wizard.SdkSelector
import javax.swing.JComponent
import javax.swing.JLabel

class PyCharmSdkSelectionPanel : SdkSelector {
    private val sdkPanel by lazy {
        sdkPanel()
    }

    override fun sdkSelectionPanel(): JComponent = sdkPanel

    override fun sdkSelectionLabel(): JLabel? = null

    private fun sdkPanel(): PyAddSdkGroupPanel {
        // Based on PyCharm's ProjectSpecificSettingsStep
        val existingSdks = getValidPythonSdks()
        val newEnvironmentPanel = PyAddNewEnvironmentPanel(existingSdks, "ff", null)
        val existingSdkPanel = PyAddExistingSdkPanel(null, null, existingSdks, "ff", existingSdks.firstOrNull())

        val defaultPanel = if (PySdkSettings.instance.useNewEnvironmentForNewProject) newEnvironmentPanel else existingSdkPanel

        val interpreterPanel = createPythonSdkPanel(listOf(newEnvironmentPanel, existingSdkPanel), defaultPanel)

        // TODO need a way to backdoor the project location interpreterPanel.newProjectPath = getNewProjectPath()

        return interpreterPanel
    }

    private fun getValidPythonSdks(): List<Sdk> = PyConfigurableInterpreterList.getInstance(null).allPythonSdks
        .asSequence()
        .filter { it.sdkType is PythonSdkType && !PythonSdkUtil.isInvalid(it) }
        .sortedWith(PreferredSdkComparator())
        .toList()

    override fun getSdk(): Sdk? = sdkPanel.getOrCreateSdk()

//     fun registerListeners() {
//        val document = step.getLocationField().textField.document
// cleanup because generators are re-used
//        if (documentListener != null) {
//            document.removeDocumentListener(documentListener)
//        }
//
//        documentListener = object : DocumentAdapter() {
//            val locationField = step.getLocationField()
//            override fun textChanged(e: DocumentEvent) {
//                sdkSelectionPanel.newProjectPath = locationField.text.trim()
//            }
//        }
//
//        document.addDocumentListener(documentListener)
//
//        sdkSelectionPanel.addChangeListener(
//            Runnable {
//                step.checkValid()
//            }
//        )
//
//        sdkSelectionPanel.newProjectPath = step.getLocationField().text.trim()
//    }

//    override fun getSdkSettings(): SdkSettings =
//        getSdk()?.let {
//            SdkBasedSdkSettings(sdk = it)
//        } ?: throw RuntimeException(message("sam.init.python.bad_sdk"))

//    private fun getSdk(): Sdk? =
//        when (val panel = sdkSelectionPanel.selectedPanel) {
//            // this list should be exhaustive
//            is PyAddNewEnvironmentPanel -> {
//                FileUtil.createDirectory(File(step.getLocationField().text.trim()))
//                panel.getOrCreateSdk()?.also {
//                    SdkConfigurationUtil.addSdk(it)
//                }
//            }
//            is PyAddExistingSdkPanel -> panel.sdk
//            else -> null
//        }

    override fun validateAll(): List<ValidationInfo>? = sdkPanel.validateAll()
}
