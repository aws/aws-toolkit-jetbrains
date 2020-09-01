// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.rds.auth

import com.intellij.database.dataSource.DataSourceUiUtil
import com.intellij.database.dataSource.LocalDataSource
import com.intellij.database.dataSource.url.template.ParametersHolder
import com.intellij.database.dataSource.url.ui.UrlPropertiesPanel
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import org.jetbrains.annotations.TestOnly
import software.aws.toolkits.jetbrains.services.rds.RdsResources
import software.aws.toolkits.jetbrains.ui.AwsAuthWidget
import software.aws.toolkits.resources.message
import javax.swing.JPanel

const val DATABASE_HOST_PROPERTY = "AWS.RdsHost"
const val DATABASE_PORT_PROPERTY = "AWS.RdsPort"

class IamAuthWidget : AwsAuthWidget() {
    private val databaseHostTextField = JBTextField()
    private val databasePortTextField = JBTextField()

    // Cache the most recently set URL and port, so if we save, and have
    // emtpy text, we set HOST and PORT properties to the current values instead of
    // having to split the datasource url string
    private var cachedUrl: String = ""
    private var cachedPort: String = ""

    override val rowCount = 4
    override fun getRegionFromUrl(url: String?): String? = RdsResources.extractRegionFromUrl(url)

    override fun createPanel(): JPanel {
        val panel = super.createPanel()
        val databaseHostLabel = JBLabel(message("rds.url")).apply { toolTipText = message("rds.iam_help") }
        val databasePortLabel = JBLabel(message("rds.port")).apply { toolTipText = message("rds.iam_help") }
        panel.add(databaseHostLabel, UrlPropertiesPanel.createLabelConstraints(3, 0, databaseHostLabel.preferredSize.getWidth()))
        panel.add(databaseHostTextField, UrlPropertiesPanel.createSimpleConstraints(3, 1, 3))
        panel.add(databasePortLabel, UrlPropertiesPanel.createLabelConstraints(3, 4, databasePortLabel.preferredSize.getWidth()))
        panel.add(databasePortTextField, UrlPropertiesPanel.createSimpleConstraints(3, 5, 1))
        return panel
    }

    override fun save(dataSource: LocalDataSource, copyCredentials: Boolean) {
        super.save(dataSource, copyCredentials)

        val host = if (databaseHostTextField.text.isNullOrBlank()) {
            cachedUrl
        } else {
            databaseHostTextField.text
        }
        DataSourceUiUtil.putOrRemove(
            dataSource.additionalJdbcProperties,
            DATABASE_HOST_PROPERTY,
            host
        )

        val port = if (databasePortTextField.text.isNullOrBlank()) {
            cachedPort
        } else {
            databasePortTextField.text
        }
        DataSourceUiUtil.putOrRemove(
            dataSource.additionalJdbcProperties,
            DATABASE_PORT_PROPERTY,
            port
        )
    }

    override fun reset(dataSource: LocalDataSource, resetCredentials: Boolean) {
        super.reset(dataSource, resetCredentials)
        databaseHostTextField.text = dataSource.additionalJdbcProperties[DATABASE_HOST_PROPERTY]
        databasePortTextField.text = dataSource.additionalJdbcProperties[DATABASE_PORT_PROPERTY]
    }

    override fun updateFromUrl(holder: ParametersHolder) {
        super.updateFromUrl(holder)
        holder.getParameter(hostParameter)?.let {
            if (it.isNotBlank()) {
                databaseHostTextField.emptyText.text = it
                cachedUrl = it
            }
        }
        holder.getParameter(portParameter)?.let {
            if (it.isNotBlank()) {
                databasePortTextField.emptyText.text = it
                cachedPort = it
            }
        }
    }

    @TestOnly
    internal fun getDatabaseHost() = cachedUrl

    @TestOnly
    internal fun getDatabasePort() = cachedPort
}
