// Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.ui.connection

import com.intellij.openapi.project.Project
import com.intellij.ui.PopupMenuListenerAdapter
import software.aws.toolkits.core.region.AwsRegion
import software.aws.toolkits.jetbrains.core.credentials.AwsConnectionManager
import software.aws.toolkits.jetbrains.core.credentials.ConnectionSettings
import software.aws.toolkits.jetbrains.core.credentials.CredentialManager
import software.aws.toolkits.jetbrains.core.region.AwsRegionProvider
import javax.swing.JComponent
import javax.swing.event.PopupMenuEvent

class AwsConnectionSettingsSelector(
    project: Project,
    private val settingsChangedListener: (ConnectionSettings?) -> Unit = { _ -> }
) {
    private val regionProvider = AwsRegionProvider.getInstance()
    private val credentialManager = CredentialManager.getInstance()
    val view = AwsConnectionSettings()

    init {
        view.region.setRegions(regionProvider.allRegions().values.toMutableList())
        view.credentialProvider.setCredentialsProviders(credentialManager.getCredentialIdentifiers())

        val accountSettingsManager = AwsConnectionManager.getInstance(project)
        if (accountSettingsManager.isValidConnectionSettings()) {
            view.region.selectedRegion = accountSettingsManager.activeRegion
            accountSettingsManager.selectedCredentialIdentifier?.let {
                view.credentialProvider.setSelectedCredentialsProvider(it)
            }

            fireChange()
        }
        view.credentialProvider.addPopupMenuListener(
            object : PopupMenuListenerAdapter() {
                override fun popupMenuWillBecomeInvisible(e: PopupMenuEvent?) {
                    fireChange()
                }
            }
        )

        view.region.addPopupMenuListener(
            object : PopupMenuListenerAdapter() {
                override fun popupMenuWillBecomeInvisible(e: PopupMenuEvent?) {
                    fireChange()
                }
            }
        )
    }

    private fun fireChange() {
        settingsChangedListener(connectionSettings())
    }

    fun selectorPanel(): JComponent = view.panel

    fun resetAwsConnectionOptions(regionId: String?, credentialProviderId: String?) {
        if (regionId != null) {
            view.region.selectedRegion = regionProvider[regionId]
        }

        if (credentialProviderId == null) {
            return
        }

        try {
            val credentialIdentifier = credentialManager.getCredentialIdentifierById(credentialProviderId)
            if (credentialIdentifier != null) {
                view.credentialProvider.setSelectedCredentialsProvider(credentialIdentifier)
            }
        } catch (_: Exception) {
            view.credentialProvider.setSelectedInvalidCredentialsProvider(credentialProviderId)
        }
    }

    fun selectedCredentialProvider(): String? = view.credentialProvider.getSelectedCredentialsProvider()

    fun selectedRegion(): AwsRegion? = view.region.selectedRegion

    fun connectionSettings() = view.region.selectedRegion?.let { region ->
        view.credentialProvider.getSelectedCredentialsProvider()?.let { credId ->
            val manager = CredentialManager.getInstance()
            manager.getCredentialIdentifierById(credId)?.let {
                ConnectionSettings(manager.getAwsCredentialProvider(it, region), region)
            }
        }
    }
}
