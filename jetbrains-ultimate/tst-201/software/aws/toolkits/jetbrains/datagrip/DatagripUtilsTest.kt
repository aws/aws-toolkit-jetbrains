// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.datagrip

import com.intellij.database.dataSource.DatabaseConnectionInterceptor
import com.intellij.testFramework.ProjectRule
import com.nhaarman.mockitokotlin2.doAnswer
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

class DatagripUtilsTest {
    @Rule
    @JvmField
    val projectRule = ProjectRule()

    private val credentialId = RuleUtils.randomName()
    private val defaultRegion = RuleUtils.randomName()

    private val mockCreds = AwsBasicCredentials.create("Access", "ItsASecret")

    @Before
    fun setUp() {
        MockCredentialsManager.getInstance().addCredentials(credentialId, mockCreds)
        MockRegionProvider.getInstance().addRegion(AwsRegion(defaultRegion, RuleUtils.randomName(), RuleUtils.randomName()))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `No credentials getAwsConnectionSettings`() {
        buildConnection(null, defaultRegion).getAwsConnectionSettings()
    }

    @Test(expected = IllegalArgumentException::class)
    fun `No region getAwsConnectionSettings`() {
        buildConnection(credentialId, null).getAwsConnectionSettings()
    }

    @Test(expected = IllegalArgumentException::class)
    fun `Invalid credentials getAwsConnectionSettings`() {
        buildConnection(credentialId + "INVALID", defaultRegion).getAwsConnectionSettings()
    }

    @Test(expected = IllegalArgumentException::class)
    fun `Invalid region getAwsConnectionSettings`() {
        buildConnection(credentialId, defaultRegion + "INVALID").getAwsConnectionSettings()
    }

    @Test
    fun `Working getAwsConnectionSettings`() {
        val creds = buildConnection(credentialId, defaultRegion).getAwsConnectionSettings()
        assertThat(creds.region.id).isEqualTo(defaultRegion)
        assertThat(creds.credentials.id).isEqualTo(credentialId)
    }

    @Test
    fun `jdbcAdapterFromRuntime works`() {
        assertThat(jdbcAdapterFromRuntime("postgres")).isEqualTo("postgresql")
        assertThat(jdbcAdapterFromRuntime("mysql")).isEqualTo("mysql")
        assertThat(jdbcAdapterFromRuntime("redshift")).isEqualTo("redshift")
        assertThat(jdbcAdapterFromRuntime("mongo")).isNull()
    }

    private fun buildConnection(
        credentials: String? = null,
        region: String? = null
    ): DatabaseConnectionInterceptor.ProtoConnection = mock {
        on { connectionPoint } doAnswer {
            mock {
                on { additionalJdbcProperties } doAnswer {
                    val m = mutableMapOf<String, String?>()
                    m[CREDENTIAL_ID_PROPERTY] = credentials
                    m[REGION_ID_PROPERTY] = region
                    m
                }
            }
        }
    }
}
