// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.redshift.auth

import com.intellij.database.dataSource.DatabaseConnectionInterceptor.ProtoConnection
import com.intellij.database.dataSource.DatabaseConnectionPoint
import com.intellij.database.dataSource.LocalDataSource
import com.intellij.testFramework.ProjectRule
import com.nhaarman.mockitokotlin2.KArgumentCaptor
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.doAnswer
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.stub
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.services.redshift.RedshiftClient
import software.amazon.awssdk.services.redshift.model.Cluster
import software.amazon.awssdk.services.redshift.model.DescribeClustersRequest
import software.amazon.awssdk.services.redshift.model.DescribeClustersResponse
import software.amazon.awssdk.services.redshift.model.GetClusterCredentialsRequest
import software.amazon.awssdk.services.redshift.model.GetClusterCredentialsResponse
import software.aws.toolkits.core.region.AwsRegion
import software.aws.toolkits.core.utils.RuleUtils
import software.aws.toolkits.jetbrains.core.MockClientManagerRule
import software.aws.toolkits.jetbrains.core.credentials.ConnectionSettings
import software.aws.toolkits.jetbrains.core.credentials.MockCredentialsManager
import software.aws.toolkits.jetbrains.core.region.MockRegionProvider
import software.aws.toolkits.jetbrains.datagrip.CREDENTIAL_ID_PROPERTY
import software.aws.toolkits.jetbrains.datagrip.REGION_ID_PROPERTY

class IamAuthTest {
    @Rule
    @JvmField
    val projectRule = ProjectRule()

    @Rule
    @JvmField
    val mockClientManager = MockClientManagerRule()

    private val mockCreds = AwsBasicCredentials.create("Access", "ItsASecret")

    private val apiAuth = IamAuth()
    private val credentialId = RuleUtils.randomName()
    private val defaultRegion = RuleUtils.randomName()
    private val region = AwsRegion(defaultRegion, RuleUtils.randomName(), RuleUtils.randomName())
    private val clusterId = RuleUtils.randomName()
    private val username = RuleUtils.randomName()

    private val redshiftSettings = RedshiftSettings(
        clusterId = clusterId,
        username = username,
        connectionSettings = ConnectionSettings(mock(), region)
    )

    @Before
    fun setUp() {
        MockCredentialsManager.getInstance().addCredentials(credentialId, mockCreds)
        MockRegionProvider.getInstance().addRegion(region)
    }

    @Test
    fun `Validate connection`() {
        apiAuth.validateConnection(buildConnection())
    }

    @Test
    // We actually don't need the URL at all for Redshift. It's nice for getting things off
    // of, but we don't need to directly use it
    fun `No URL`() {
        apiAuth.validateConnection(buildConnection(hasUrl = true))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `No username`() {
        apiAuth.validateConnection(buildConnection(hasUsername = false))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `No region`() {
        apiAuth.validateConnection(buildConnection(hasUsername = false))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `No credentials`() {
        apiAuth.validateConnection(buildConnection(hasCredentials = false))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `No cluster ID`() {
        apiAuth.validateConnection(buildConnection(hasClusterId = false))
    }

    @Test
    // We  don't need the URL at all for Redshift.
    fun `No host`() {
        apiAuth.validateConnection(buildConnection(hasHost = false))
    }

    @Test
    // We don't need the port either
    fun `No port`() {
        apiAuth.validateConnection(buildConnection(hasPort = false))
    }

    @Test
    fun `Get credentials succeeds`() {
        val password = RuleUtils.randomName()
        val (createCaptor, redshiftMock) = getWorkingRedshiftMock(password)
        val creds = apiAuth.getCredentials(redshiftSettings, redshiftMock)
        assertThat(creds.userName).isEqualTo(redshiftSettings.username)
        assertThat(creds.password).isEqualTo(password)
        assertThat(createCaptor.firstValue.autoCreate()).isFalse()
        assertThat(createCaptor.firstValue.dbUser()).isEqualTo(redshiftSettings.username)
        assertThat(createCaptor.firstValue.clusterIdentifier()).isEqualTo(clusterId)
    }

    @Test(expected = Exception::class)
    fun `Get credentials fails`() {
        val redshiftMock = mockClientManager.create<RedshiftClient>()
        redshiftMock.stub {
            on { describeClusters(any<DescribeClustersRequest>()) } doReturn DescribeClustersResponse.builder().clusters(mutableListOf()).build()
        }
        apiAuth.getCredentials(redshiftSettings, redshiftMock)
    }

    @Test
    fun `Intercept credentials succeeds`() {
        val password = RuleUtils.randomName()
        // we call this for the side effects only in this function
        getWorkingRedshiftMock(password)
        val connection = apiAuth.intercept(buildConnection(), false)?.toCompletableFuture()?.get()
        assertThat(connection).isNotNull
        assertThat(connection!!.connectionProperties).containsKey("user")
        assertThat(connection.connectionProperties["user"]).isEqualTo(username)
        assertThat(connection.connectionProperties).containsKey("password")
        assertThat(connection.connectionProperties["password"]).isEqualTo(password)
    }

    @Test(expected = Exception::class)
    fun `Intercept credentials fails`() {
        apiAuth.intercept(buildConnection(hasUrl = false), false)?.toCompletableFuture()?.get()
    }

    @Test(expected = IllegalStateException::class)
    fun `Get credentials cluster does not exist`() {
        val redshiftMock = mockClientManager.create<RedshiftClient>()
        redshiftMock.stub {
            on { describeClusters(any<DescribeClustersRequest>()) } doReturn DescribeClustersResponse.builder()
                .clusters(Cluster.builder().clusterIdentifier(clusterId).build())
                .build()
            on { getClusterCredentials(any<GetClusterCredentialsRequest>()) } doThrow IllegalStateException("Something wrong with creds")
        }
        apiAuth.getCredentials(redshiftSettings, redshiftMock)
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
            on { username } doReturn if (hasUsername) username else ""
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
            val m = mutableMapOf<String, String>()
            on { connectionPoint } doReturn dbConnectionPoint
            on { runConfiguration } doAnswer {
                mock {
                    on { project } doAnswer { projectRule.project }
                }
            }
            on { connectionProperties } doReturn m
        }
    }

    private fun getWorkingRedshiftMock(password: String): Pair<KArgumentCaptor<GetClusterCredentialsRequest>, RedshiftClient> {
        val redshiftMock = mockClientManager.create<RedshiftClient>()
        val createCaptor = argumentCaptor<GetClusterCredentialsRequest>()
        redshiftMock.stub {
            on { describeClusters(any<DescribeClustersRequest>()) } doReturn DescribeClustersResponse.builder()
                .clusters(Cluster.builder().clusterIdentifier(clusterId).build())
                .build()
            on { getClusterCredentials(createCaptor.capture()) } doReturn GetClusterCredentialsResponse.builder()
                .dbUser(redshiftSettings.username)
                .dbPassword(password)
                .build()
        }
        return Pair(createCaptor, redshiftMock)
    }
}
