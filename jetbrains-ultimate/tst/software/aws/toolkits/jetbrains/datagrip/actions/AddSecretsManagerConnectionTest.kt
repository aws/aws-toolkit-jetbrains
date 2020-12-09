// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.datagrip.actions

import com.intellij.database.autoconfig.DataSourceRegistry
import com.intellij.testFramework.ProjectRule
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.aws.toolkits.core.region.AwsRegion
import software.aws.toolkits.core.utils.RuleUtils
import software.aws.toolkits.jetbrains.core.credentials.MockCredentialManagerRule
import software.aws.toolkits.jetbrains.core.region.MockRegionProviderRule
import software.aws.toolkits.jetbrains.datagrip.CREDENTIAL_ID_PROPERTY
import software.aws.toolkits.jetbrains.datagrip.REGION_ID_PROPERTY
import software.aws.toolkits.jetbrains.datagrip.auth.SECRET_ID_PROPERTY
import software.aws.toolkits.jetbrains.datagrip.auth.SecretsManagerAuth
import software.aws.toolkits.jetbrains.datagrip.auth.SecretsManagerDbSecret

class AddSecretsManagerConnectionTest {
    @Rule
    @JvmField
    val projectRule = ProjectRule()

    @Rule
    @JvmField
    val regionProvider = MockRegionProviderRule()

    @Rule
    @JvmField
    val credentialManager = MockCredentialManagerRule()

    private val credentialId = RuleUtils.randomName()
    private val defaultRegion = RuleUtils.randomName()
    private val mockCreds = AwsBasicCredentials.create("Access", "ItsASecret")

    @Before
    fun setUp() {
        credentialManager.addCredentials(credentialId, mockCreds)
        regionProvider.addRegion(AwsRegion(defaultRegion, RuleUtils.randomName(), RuleUtils.randomName()))
    }

    @Test
    fun `Add data source`() {
        val port = RuleUtils.randomNumber()
        val address = RuleUtils.randomName()
        val username = RuleUtils.randomName()
        val password = RuleUtils.randomName()
        val secretArn = RuleUtils.randomName()
        val engine = RuleUtils.randomName()
        val registry = DataSourceRegistry(projectRule.project)
        registry.createDatasource(
            projectRule.project,
            SecretsManagerDbSecret(username, password, engine, address, port.toString()),
            secretArn,
            "adapter"
        )
        assertThat(registry.newDataSources).hasOnlyOneElementSatisfying {
            assertThat(it.isTemporary).isFalse()
            assertThat(it.sslCfg?.myEnabled).isTrue()
            assertThat(it.url).isEqualTo("jdbc:adapter://$address:$port")
            assertThat(it.additionalJdbcProperties[CREDENTIAL_ID_PROPERTY]).isEqualTo(credentialId)
            assertThat(it.additionalJdbcProperties[REGION_ID_PROPERTY]).isEqualTo(defaultRegion)
            assertThat(it.additionalJdbcProperties[SECRET_ID_PROPERTY]).isEqualTo(secretArn)
            assertThat(it.authProviderId).isEqualTo(SecretsManagerAuth.providerId)
        }
    }
}
