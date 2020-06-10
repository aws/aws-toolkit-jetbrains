// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.rds.auth

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

class IamAuthTest {
    @Rule
    @JvmField
    val projectRule = ProjectRule()

    private val iamAuth = IamAuth()
    private val credentialId = RuleUtils.randomName()
    private val defaultRegion = RuleUtils.randomName()

    private val mockCreds = AwsBasicCredentials.create("Access", "ItsASecret")

    @Before
    fun setUp() {
        MockCredentialsManager.getInstance().addCredentials(credentialId, mockCreds)
        MockRegionProvider.getInstance().addRegion(AwsRegion(defaultRegion, RuleUtils.randomName(), RuleUtils.randomName()))
    }

    @Test
    fun validateConnection() {
        iamAuth.validateConnection(buildConnection())
    }

    @Test(expected = IllegalArgumentException::class)
    fun validateConnectionNoUrl() {
        iamAuth.validateConnection(buildConnection(hasUrl = false))
    }

    @Test(expected = IllegalArgumentException::class)
    fun validateConnectionNoUsername() {
        iamAuth.validateConnection(buildConnection(hasUsername = false))
    }

    @Test(expected = IllegalArgumentException::class)
    fun validateConnectionNoRegion() {
        iamAuth.validateConnection(buildConnection(hasUsername = false))
    }

    @Test(expected = IllegalArgumentException::class)
    fun validateConnectionNoCredentials() {
        iamAuth.validateConnection(buildConnection(hasCredentials = false))
    }

    @Test(expected = IllegalArgumentException::class)
    fun validateConnectionNoPort() {
        iamAuth.validateConnection(buildConnection(hasPort = false))
    }
    
    @Test(expected = IllegalArgumentException::class)
    fun validateConnectionNoHost() {
        iamAuth.validateConnection(buildConnection(hasHost = false))
    }

    private fun buildConnection(
        hasUrl: Boolean = true,
        hasUsername: Boolean = true,
        hasRegion: Boolean = true,
        hasCredentials: Boolean = true,
        hasHost: Boolean = true,
        hasPort: Boolean = true
    ): ProtoConnection {
        val mockConnection = mock<LocalDataSource> {
            on { url } doReturn if (hasUrl) "jdbc:postgresql://${if (hasHost) "coolpostgresdb" else ""}.555555.us-west-2.rds.amazonaws.com${if (hasPort) ":5432" else ""}/dev" else null
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
                m
            }
            on { dataSource } doReturn mockConnection
        }
        return mock {
            on { connectionPoint } doReturn dbConnectionPoint
        }
    }
}
