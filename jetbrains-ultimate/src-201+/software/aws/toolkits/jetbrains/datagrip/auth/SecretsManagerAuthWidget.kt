// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.datagrip.auth

import com.intellij.database.dataSource.DataSourceUiUtil
import com.intellij.database.dataSource.LocalDataSource
import com.intellij.database.dataSource.url.ui.UrlPropertiesPanel
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.util.text.nullize
import org.jetbrains.annotations.TestOnly
import software.aws.toolkits.jetbrains.services.rds.RdsResources
import software.aws.toolkits.jetbrains.services.redshift.RedshiftUtils
import software.aws.toolkits.jetbrains.ui.AwsAuthWidget
import software.aws.toolkits.resources.message
import javax.swing.JPanel

const val SECRET_ID_PROPERTY = "AWS.SecretId"
const val GET_URL_FROM_SECRET = "AWS.getUrlFromSecret"

class SecretsManagerAuthWidget : AwsAuthWidget(userField = false) {
    private val secretIdSelector = JBTextField()
    private val urlFromSecret = JBCheckBox(message("datagrip.secret_host"))

    override val rowCount = 5
    override fun getRegionFromUrl(url: String?): String? = RdsResources.extractRegionFromUrl(url) ?: RedshiftUtils.extractRegionFromUrl(url)

    override fun createPanel(): JPanel {
        val panel = super.createPanel()
        val secretLabel = JBLabel(message("datagrip.secret_id"))
        panel.add(secretLabel, UrlPropertiesPanel.createLabelConstraints(3, 0, secretLabel.preferredSize.getWidth()))
        panel.add(secretIdSelector, UrlPropertiesPanel.createSimpleConstraints(3, 1, 3))
        panel.add(urlFromSecret, UrlPropertiesPanel.createSimpleConstraints(4, 1, 3))
        return panel
    }

    override fun save(dataSource: LocalDataSource, copyCredentials: Boolean) {
        super.save(dataSource, copyCredentials)

        DataSourceUiUtil.putOrRemove(
            dataSource.additionalJdbcProperties,
            SECRET_ID_PROPERTY,
            secretIdSelector.text.nullize()
        )

        DataSourceUiUtil.putOrRemove(
            dataSource.additionalJdbcProperties,
            GET_URL_FROM_SECRET,
            urlFromSecret.isSelected.toString()
        )
    }

    override fun reset(dataSource: LocalDataSource, resetCredentials: Boolean) {
        super.reset(dataSource, resetCredentials)
        dataSource.additionalJdbcProperties[SECRET_ID_PROPERTY]?.nullize()?.let {
            secretIdSelector.text = it
        }
        dataSource.additionalJdbcProperties[GET_URL_FROM_SECRET]?.nullize()?.let {
            urlFromSecret.isSelected = it.toBoolean()
        }
    }

    @TestOnly
    internal fun getSecretId() = secretIdSelector.text
    @TestOnly
    internal fun getUrlFromSecretSet() = urlFromSecret.isSelected
}
