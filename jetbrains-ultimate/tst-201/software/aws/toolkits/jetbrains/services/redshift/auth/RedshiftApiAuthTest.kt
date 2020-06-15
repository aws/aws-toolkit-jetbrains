// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.redshift.auth

import com.intellij.database.dataSource.DatabaseConnectionInterceptor.ProtoConnection
import com.intellij.database.dataSource.DatabaseConnectionPoint
import com.intellij.database.dataSource.LocalDataSource
import com.intellij.testFramework.ProjectRule
import com.nhaarman.mockitokotlin2.doAnswer
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.aws.toolkits.core.region.AwsRegion
import software.aws.toolkits.core.utils.RuleUtils
import software.aws.toolkits.jetbrains.core.credentials.MockCredentialsManager
import software.aws.toolkits.jetbrains.core.region.MockRegionProvider
import software.aws.toolkits.jetbrains.ui.CREDENTIAL_ID_PROPERTY
import software.aws.toolkits.jetbrains.ui.REGION_ID_PROPERTY

class RedshiftApiAuthTest {
    @Rule
    @JvmField
    val projectRule = ProjectRule()

    private val apiAuth = ApiAuth()
    private val credentialId = RuleUtils.randomName()
    private val defaultRegion = RuleUtils.randomName()
    private val clusterId = RuleUtils.randomName()

    private val mockCreds = AwsBasicCredentials.create("Access", "ItsASecret")

    @Before
    fun setUp() {
        MockCredentialsManager.getInstance().addCredentials(credentialId, mockCreds)
        MockRegionProvider.getInstance().addRegion(AwsRegion(defaultRegion, RuleUtils.randomName(), RuleUtils.randomName()))
    }

    @Test
    fun validateConnection() {
        apiAuth.validateConnection(buildConnection())
    }

    @Test
    // We actually don't need the URL at all for Redshift. It's nice for getting things off
    // of, but we don't need to directly use it
    fun validateConnectionNoUrl() {
        apiAuth.validateConnection(buildConnection(hasUrl = true))
    }

    @Test(expected = IllegalArgumentException::class)
    fun validateConnectionNoUsername() {
        apiAuth.validateConnection(buildConnection(hasUsername = false))
    }

    @Test(expected = IllegalArgumentException::class)
    fun validateConnectionNoRegion() {
        apiAuth.validateConnection(buildConnection(hasUsername = false))
    }

    @Test(expected = IllegalArgumentException::class)
    fun validateConnectionNoCredentials() {
        apiAuth.validateConnection(buildConnection(hasCredentials = false))
    }

    @Test(expected = IllegalArgumentException::class)
    fun validateConnectionNoClusterId() {
        apiAuth.validateConnection(buildConnection(hasClusterId = false))
    }

    @Test
    // We actually don't need the URL at all for Redshift. It's nice for getting things off
    // of, but we don't need to directly use it
    fun validateConnectionNoHost() {
        apiAuth.validateConnection(buildConnection(hasHost = false))
    }

    @Test
    // We actually don't need the port either
    fun validateConnectionNoPort() {
        apiAuth.validateConnection(buildConnection(hasPort = false))
    }

    private fun buildConnection(
        hasUrl: Boolean = true,
        hasUsername: Boolean = true,
        hasRegion: Boolean = true,
        hasCredentials: Boolean = true,
        hasHost: Boolean = true,
        hasClusterId: Boolean = true,
        hasPort: Boolean = true
    ): ProtoConnection {
        val mockConnection = mock<LocalDataSource> {
            on { url } doReturn if (hasUrl) {
                "jdbc:postgresql://${if (hasHost) "redshift-cluster-1.555555.us-west-2.redshift.amazonaws.com" else ""}${if (hasPort) ":5432" else ""}/dev"
            } else {
                null
            }
            on { databaseDriver } doReturn null
            on { driverClass } doReturn "org.postgresql.Driver"
            on { username } doReturn if (hasUsername) RuleUtils.randomName() else ""
        }
        val dbConnectionPoint = mock<DatabaseConnectionPoint> {
            on { additionalJdbcProperties } doAnswer {
                val m = mutableMapOf<String, String>()
                if (hasCredentials) {
                    m[CREDENTIAL_ID_PROPERTY] = credentialId
                }
                if (hasRegion) {
                    m[REGION_ID_PROPERTY] = defaultRegion
                }
                if (hasClusterId) {
                    m[CLUSTER_ID_PROPERTY] = clusterId
                }
                m
            }
            on { dataSource } doReturn mockConnection
        }
        return mock {
            on { connectionPoint } doReturn dbConnectionPoint
        }
    }
}
