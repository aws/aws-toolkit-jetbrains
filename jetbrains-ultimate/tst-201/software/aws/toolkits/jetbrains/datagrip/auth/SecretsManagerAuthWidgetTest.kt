// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.datagrip.auth

import com.intellij.database.dataSource.LocalDataSource
import com.intellij.database.dataSource.url.template.UrlEditorModel
import com.intellij.testFramework.ProjectRule
import com.nhaarman.mockitokotlin2.doAnswer
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.aws.toolkits.core.region.AwsRegion
import software.aws.toolkits.core.utils.RuleUtils
import software.aws.toolkits.jetbrains.core.credentials.MockCredentialsManager
import software.aws.toolkits.jetbrains.core.region.MockRegionProvider
import software.aws.toolkits.jetbrains.datagrip.CREDENTIAL_ID_PROPERTY
import software.aws.toolkits.jetbrains.datagrip.REGION_ID_PROPERTY

class SecretsManagerAuthWidgetTest {
    @Rule
    @JvmField
    val projectRule = ProjectRule()

    private lateinit var widget: SecretsManagerAuthWidget
    private val credentialId = RuleUtils.randomName()
    private val defaultRegion = RuleUtils.randomName()
    private val defaultSecretId = RuleUtils.randomName()
    private val mockCreds = AwsBasicCredentials.create("Access", "ItsASecret")

    @Before
    fun setUp() {
        widget = SecretsManagerAuthWidget()
        MockCredentialsManager.getInstance().addCredentials(credentialId, mockCreds)
        MockRegionProvider.getInstance().addRegion(AwsRegion(defaultRegion, RuleUtils.randomName(), RuleUtils.randomName()))
    }

    @Test
    fun `No secret set is empty in widget`() {
        widget.reset(buildDataSource(hasSecret = false), false)
        assertThat(widget.getSecretId()).isEmpty()
    }

    @Test
    fun `Secret set from widget`() {
        widget.reset(buildDataSource(hasSecret = true), false)
        assertThat(widget.getSecretId()).isEqualTo(defaultSecretId)
    }

    @Test
    fun `Sets region from Redshift URL`() {
        widget.reset(mock(), false)
        val endpointUrl = "jdbc:redshift://redshift-cluster.host.$defaultRegion.redshift.amazonaws.com:5439/dev"
        widget.updateFromUrl(mock<UrlEditorModel> { on { url } doReturn endpointUrl })
        assertThat(widget.getSelectedRegion()?.id).isEqualTo(defaultRegion)
    }

    @Test
    fun `Sets region from RDS URL`() {
        widget.reset(mock(), false)
        val endpointUrl = "jdbc:postgresql://abc.host.$defaultRegion.rds.amazonaws.com:5432/dev"
        widget.updateFromUrl(mock<UrlEditorModel> { on { url } doReturn endpointUrl })
        assertThat(widget.getSelectedRegion()?.id).isEqualTo(defaultRegion)
    }

    @Test
    fun `Does not unset region on invalid url`() {
        widget.reset(mock(), false)
        val endpointUrl = "jdbc:postgresql://abc.host.$defaultRegion.rds.amazonaws.com:5432/dev"
        widget.updateFromUrl(mock<UrlEditorModel> { on { url } doReturn endpointUrl })
        val badUrl = "jdbc:postgresql://abc.host.1000000%invalidregion.rds.amazonaws.com:5432/dev"
        widget.updateFromUrl(mock<UrlEditorModel> { on { url } doReturn badUrl })
        assertThat(widget.getSelectedRegion()?.id).isEqualTo(defaultRegion)
    }

    private fun buildDataSource(hasSecret: Boolean = true): LocalDataSource = mock {
        on { additionalJdbcProperties } doAnswer {
            mutableMapOf<String, String>().also {
                it[CREDENTIAL_ID_PROPERTY] = credentialId
                it[REGION_ID_PROPERTY] = defaultRegion
                if (hasSecret) {
                    it[SECRET_ID_PROPERTY] = defaultSecretId
                }
            }
        }
    }
}
