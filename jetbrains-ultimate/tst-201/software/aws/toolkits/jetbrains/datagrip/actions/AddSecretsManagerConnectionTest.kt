// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.datagrip.actions

import com.intellij.database.autoconfig.DataSourceRegistry
import com.intellij.testFramework.ProjectRule
import org.assertj.core.api.Assertions
import org.junit.Rule
import org.junit.Test
import software.aws.toolkits.core.utils.RuleUtils
import software.aws.toolkits.jetbrains.core.credentials.MockCredentialsManager
import software.aws.toolkits.jetbrains.core.region.MockRegionProvider
import software.aws.toolkits.jetbrains.datagrip.CREDENTIAL_ID_PROPERTY
import software.aws.toolkits.jetbrains.datagrip.REGION_ID_PROPERTY
import software.aws.toolkits.jetbrains.datagrip.auth.SECRET_ID_PROPERTY
import software.aws.toolkits.jetbrains.datagrip.auth.SecretsManagerAuth
import software.aws.toolkits.jetbrains.datagrip.auth.SecretsManagerDbSecret

class AddSecretsManagerConnectionTest {
    @Rule
    @JvmField
    val projectRule = ProjectRule()

    @Test
    fun `Add data source`() {
        val port = 999
        val address = RuleUtils.randomName()
        val username = RuleUtils.randomName()
        val password = RuleUtils.randomName()
        val dbName = RuleUtils.randomName()
        val secretArn = RuleUtils.randomName()
        val engine = RuleUtils.randomName()
        val registry = DataSourceRegistry(projectRule.project)
        registry.createDatasource(
            projectRule.project,
            SecretsManagerDbSecret(username, password, engine, address, port.toString()),
            secretArn,
            "adapter"
        )
        Assertions.assertThat(registry.newDataSources).hasOnlyOneElementSatisfying {
            Assertions.assertThat(it.isTemporary).isFalse()
            Assertions.assertThat(it.sslCfg?.myEnabled).isTrue()
            Assertions.assertThat(it.url).isEqualTo("jdbc:adapter://$address:$port")
            Assertions.assertThat(it.additionalJdbcProperties[CREDENTIAL_ID_PROPERTY]).isEqualTo(MockCredentialsManager.DUMMY_PROVIDER_IDENTIFIER.displayName)
            Assertions.assertThat(it.additionalJdbcProperties[REGION_ID_PROPERTY]).isEqualTo(MockRegionProvider.getInstance().defaultRegion().id)
            Assertions.assertThat(it.additionalJdbcProperties[SECRET_ID_PROPERTY]).isEqualTo(secretArn)
            Assertions.assertThat(it.authProviderId).isEqualTo(SecretsManagerAuth.providerId)
        }
    }
}
