// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.rds.auth

import com.intellij.database.dataSource.DataSourceUiUtil
import com.intellij.database.dataSource.DatabaseCredentialsAuthProvider
import com.intellij.database.dataSource.LocalDataSource
import com.intellij.database.dataSource.url.template.ParametersHolder
import com.intellij.database.dataSource.url.template.UrlEditorModel
import com.intellij.database.dataSource.url.ui.UrlPropertiesPanel
import com.intellij.ui.components.JBLabel
import com.intellij.uiDesigner.core.GridLayoutManager
import com.intellij.util.text.nullize
import software.aws.toolkits.core.utils.getLogger
import software.aws.toolkits.jetbrains.core.credentials.CredentialManager
import software.aws.toolkits.jetbrains.core.region.AwsRegionProvider
import software.aws.toolkits.jetbrains.services.rds.RdsResources
import software.aws.toolkits.jetbrains.ui.CredentialProviderSelector
import software.aws.toolkits.jetbrains.ui.RegionSelector
import software.aws.toolkits.resources.message
import javax.swing.JPanel

// TODO put these somewhere else
const val CREDENTIAL_ID_PROPERTY = "AWS.CredentialId"
const val REGION_ID_PROPERTY = "AWS.RegionId"

class IamAuthWidget : DatabaseCredentialsAuthProvider.UserWidget() {
    private val credentialSelector = CredentialProviderSelector()
    private val regionSelector = RegionSelector()

    override fun createPanel(): JPanel {
        val panel = JPanel(GridLayoutManager(3, 6))
        addUserField(panel, 0)

        val credsLabel = JBLabel(message("aws_connection.credentials.label"))
        val regionLabel = JBLabel(message("aws_connection.region.label"))
        panel.add(credsLabel, UrlPropertiesPanel.createLabelConstraints(1, 0, credsLabel.preferredSize.getWidth()))
        panel.add(credentialSelector, UrlPropertiesPanel.createSimpleConstraints(1, 1, 3))
        panel.add(regionLabel, UrlPropertiesPanel.createLabelConstraints(2, 0, regionLabel.preferredSize.getWidth()))
        panel.add(regionSelector, UrlPropertiesPanel.createSimpleConstraints(2, 1, 3))

        regionSelector.setRegions(AwsRegionProvider.getInstance().allRegions().values.toMutableList())

        return panel
    }

    override fun save(dataSource: LocalDataSource, copyCredentials: Boolean) {
        super.save(dataSource, copyCredentials)

        DataSourceUiUtil.putOrRemove(dataSource.additionalJdbcProperties, CREDENTIAL_ID_PROPERTY, credentialSelector.getSelectedCredentialsProvider())
        DataSourceUiUtil.putOrRemove(dataSource.additionalJdbcProperties, REGION_ID_PROPERTY, regionSelector.selectedRegion?.id)
    }

    override fun reset(dataSource: LocalDataSource, resetCredentials: Boolean) {
        super.reset(dataSource, resetCredentials)

        val credentialManager = CredentialManager.getInstance()
        credentialSelector.setCredentialsProviders(credentialManager.getCredentialIdentifiers())

        val credentialId = dataSource.additionalJdbcProperties[CREDENTIAL_ID_PROPERTY]?.nullize()

        credentialId?.let {
            credentialManager.getCredentialIdentifierById(credentialId)?.let {
                credentialSelector.setSelectedCredentialsProvider(it)
                return
            }
        }

        credentialSelector.setSelectedInvalidCredentialsProvider(credentialId)
    }

    override fun isPasswordChanged(): Boolean = false

    override fun updateFromUrl(holder: ParametersHolder) {
        // Try to get region from url
        val url = (holder as? UrlEditorModel)?.url
        val regionId = RdsResources.extractRegionFromUrl(url)
        val region = AwsRegionProvider.getInstance().allRegions()[regionId]
        region?.let {
            regionSelector.selectedRegion = region
        }
        super.updateFromUrl(holder)
    }
}
