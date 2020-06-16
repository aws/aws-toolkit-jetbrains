// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.redshift.auth

import com.intellij.database.dataSource.DataSourceUiUtil
import com.intellij.database.dataSource.LocalDataSource
import com.intellij.database.dataSource.url.template.ParametersHolder
import com.intellij.database.dataSource.url.template.UrlEditorModel
import com.intellij.database.dataSource.url.ui.UrlPropertiesPanel
import com.intellij.ide.impl.ProjectUtil
import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBLabel
import software.amazon.awssdk.services.secretsmanager.model.SecretListEntry
import software.aws.toolkits.jetbrains.services.redshift.extractClusterIdFromUrl
import software.aws.toolkits.jetbrains.services.redshift.extractRegionFromUrl
import software.aws.toolkits.jetbrains.services.secretsmanager.SecretsManagerResources
import software.aws.toolkits.jetbrains.ui.AwsAuthWidget
import software.aws.toolkits.jetbrains.ui.ResourceSelector
import software.aws.toolkits.resources.message
import javax.swing.JPanel
import javax.swing.event.DocumentListener

const val SECRET_ID_PROPERTY = "AWS.SecretId"

class SecretsManagerAuthWidget : AwsAuthWidget() {
    private lateinit var secretIdSelector: ResourceSelector<SecretListEntry>

    override val rowCount = 4
    override fun getRegionFromUrl(url: String?): String? = extractRegionFromUrl(url)

    override fun createPanel(): JPanel {
        val panel = super.createPanel()
        val regionLabel = JBLabel(message("redshift.cluster_id"))
       /* secretIdSelector  ResourceSelector
            .builder(project)
            .resource(SecretsManagerResources.secrets)
            .build()*/
        panel.add(regionLabel, UrlPropertiesPanel.createLabelConstraints(3, 0, regionLabel.preferredSize.getWidth()))
        panel.add(secretIdSelector, UrlPropertiesPanel.createSimpleConstraints(3, 1, 3))
        return panel
    }

    override fun save(dataSource: LocalDataSource, copyCredentials: Boolean) {
        super.save(dataSource, copyCredentials)

        DataSourceUiUtil.putOrRemove(
            dataSource.additionalJdbcProperties,
            SECRET_ID_PROPERTY,
            secretIdSelector.selected()?.arn()
        )
    }

    override fun reset(dataSource: LocalDataSource, resetCredentials: Boolean) {
        super.reset(dataSource, resetCredentials)
        dataSource.additionalJdbcProperties[SECRET_ID_PROPERTY]?.let {
            secretIdSelector.selectedItem = it
        }
    }

    override fun onChanged(r: DocumentListener) {
        super.onChanged(r)
    }
}
