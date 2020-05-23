// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0
package software.aws.toolkits.jetbrains.core.execution

import com.intellij.execution.configurations.RunConfigurationBase
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project
import software.aws.toolkits.core.credentials.ToolkitCredentialsIdentifier
import software.aws.toolkits.jetbrains.core.credentials.CredentialManager
import software.aws.toolkits.jetbrains.core.credentials.activeConnection
import software.aws.toolkits.jetbrains.core.region.AwsRegionProvider
import software.aws.toolkits.jetbrains.ui.CredentialProviderSelector
import software.aws.toolkits.jetbrains.ui.RegionSelector
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JRadioButton

class AwsConnectionRunConfigurationExtensionSettingsPanel {
    internal lateinit var panel: JPanel
    internal lateinit var none: JRadioButton
    internal lateinit var useCurrentConnection: JRadioButton
    internal lateinit var manuallyConfiguredConnection: JRadioButton
    internal lateinit var credentialProvider: CredentialProviderSelector
    internal lateinit var region: RegionSelector
}

class AwsConnectionRunConfigurationExtensionSettingsEditor<T : RunConfigurationBase<*>?>(private val project: Project) : SettingsEditor<T>() {
    internal val view = AwsConnectionRunConfigurationExtensionSettingsPanel()
    private val regionProvider = AwsRegionProvider.getInstance()
    private val credentialManager = CredentialManager.getInstance()

    init {
        view.manuallyConfiguredConnection.addActionListener { updateComponents() }
        view.none.addActionListener { updateComponents() }
        view.useCurrentConnection.addActionListener { updateComponents() }
    }

    override fun resetEditorFrom(configuration: T) {
        configuration?.getCopyableUserData(AWS_CONNECTION_RUN_CONFIGURATION_KEY)?.let { config ->
            view.region.isEnabled = false
            view.credentialProvider.isEnabled = false
            when {
                config.useCurrentConnection -> {
                    view.useCurrentConnection.isSelected = true
                }
                config.region != null || config.credential != null -> {
                    view.manuallyConfiguredConnection.isSelected = true
                    populateRegionsIfRequired()
                    populateCredentialsIfRequired()
                    view.region.isEnabled = true
                    view.credentialProvider.isEnabled = true

                    view.region.selectedRegion = config.region?.let { regionProvider.lookupRegionById(it) }
                    when (val credential = config.credential) {
                        is String -> view.credentialProvider.setSelectedCredential(credential)
                        else -> view.credentialProvider.selectedItem = null
                    }
                }
                else -> {
                    view.none.isSelected = true
                }
            }
        }
    }

    override fun createEditor(): JComponent = view.panel

    override fun applyEditorTo(configuration: T) {
        configuration?.putCopyableUserData(
            AWS_CONNECTION_RUN_CONFIGURATION_KEY,
            AwsConnectionRunConfigurationExtensionOptions().also {
                when {
                    view.useCurrentConnection.isSelected -> it.useCurrentConnection = true
                    view.manuallyConfiguredConnection.isSelected -> {
                        it.region = view.region.selectedRegion?.id
                        it.credential = view.credentialProvider.getSelectedCredentialsProvider()
                    }
                }
            }
        )
    }

    private fun updateComponents() {
        if (view.manuallyConfiguredConnection.isSelected) {
            populateRegionsIfRequired()
            populateCredentialsIfRequired()
            if (view.region.selectedRegion == null || view.credentialProvider.selectedItem == null) {
                project.activeConnection()?.run {
                    if (view.region.selectedRegion == null) {
                        view.region.selectedRegion = region
                    }
                    if (view.credentialProvider.selectedItem == null) {
                        view.credentialProvider.setSelectedCredential(credentials.id)
                    }
                }
            }
        }
        view.region.isEnabled = view.manuallyConfiguredConnection.isSelected
        view.credentialProvider.isEnabled = view.manuallyConfiguredConnection.isSelected
    }

    private fun populateCredentialsIfRequired() {
        if (view.credentialProvider.itemCount == 0) {
            view.credentialProvider.setCredentialsProviders(credentialManager.getCredentialIdentifiers())
        }
    }

    private fun populateRegionsIfRequired() {
        if (view.region.itemCount == 0) {
            view.region.setRegions(regionProvider.allRegions().values.toList())
        }
    }

    private fun CredentialProviderSelector.setSelectedCredential(id: String) {
        when (val identifier = credentialManager.getCredentialIdentifierById(id)) {
            is ToolkitCredentialsIdentifier -> setSelectedCredentialsProvider(identifier)
            else -> setSelectedInvalidCredentialsProvider(id)
        }
    }
}
