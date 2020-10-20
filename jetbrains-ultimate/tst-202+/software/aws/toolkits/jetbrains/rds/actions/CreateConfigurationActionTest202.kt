// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0


// FIX_WHEN_MIN_IS_202 merge this with the normal one
class CreateConfigurationActionTest202 {

    @Test
    fun `Add Aurora MySQL data source`() {
        val instance = createDbInstance(address = address, port = port, engineType = "aurora")
        val registry = DataSourceRegistry(projectRule.project)
        registry.createRdsDatasource(
            RdsDatasourceConfiguration(
                username = username,
                credentialId = MockCredentialsManager.DUMMY_PROVIDER_IDENTIFIER.id,
                regionId = MockRegionProvider.getInstance().defaultRegion().id,
                dbInstance = instance
            )
        )
        assertThat(registry.newDataSources).hasOnlyOneElementSatisfying {
            assertThat(it.username).isEqualTo(username)
            assertThat(it.driverClass).contains("mariadb")
            assertThat(it.url).contains(jdbcMysqlAurora)
            assertThat(it.sslCfg?.myMode).isEqualTo(SslMode.REQUIRE)
        }
    }

    @Test
    fun `Add Aurora MySQL 5_7 data source`() {
        val instance = createDbInstance(address = address, port = port, engineType = "aurora-mysql")
        val registry = DataSourceRegistry(projectRule.project)
        registry.createRdsDatasource(
            RdsDatasourceConfiguration(
                username = username,
                credentialId = MockCredentialsManager.DUMMY_PROVIDER_IDENTIFIER.id,
                regionId = MockRegionProvider.getInstance().defaultRegion().id,
                dbInstance = instance
            )
        )
        assertThat(registry.newDataSources).hasOnlyOneElementSatisfying {
            assertThat(it.username).isEqualTo(username)
            assertThat(it.driverClass).contains("mariadb")
            assertThat(it.url).contains(jdbcMysqlAurora)
            assertThat(it.sslCfg?.myMode).isEqualTo(SslMode.REQUIRE)
        }
    }
}
