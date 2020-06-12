// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.redshift.auth

import com.intellij.credentialStore.Credentials
import com.intellij.database.access.DatabaseCredentials
import com.intellij.database.dataSource.DatabaseAuthProvider
import com.intellij.database.dataSource.DatabaseAuthProvider.AuthWidget
import com.intellij.database.dataSource.DatabaseConnectionInterceptor.ProtoConnection
import com.intellij.database.dataSource.DatabaseCredentialsAuthProvider
import com.intellij.database.dataSource.LocalDataSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.future.future
import software.amazon.awssdk.services.redshift.RedshiftClient
import software.aws.toolkits.core.utils.getLogger
import software.aws.toolkits.core.utils.info
import software.aws.toolkits.jetbrains.core.awsClient
import software.aws.toolkits.jetbrains.core.credentials.CredentialManager
import software.aws.toolkits.jetbrains.core.region.AwsRegionProvider
import software.aws.toolkits.jetbrains.ui.CREDENTIAL_ID_PROPERTY
import software.aws.toolkits.jetbrains.ui.REGION_ID_PROPERTY
import software.aws.toolkits.jetbrains.utils.ApplicationThreadPoolScope
import software.aws.toolkits.resources.message
import java.util.concurrent.CompletionStage

// This is marked as internal but is what we were told to use
class ApiAuth : DatabaseAuthProvider, CoroutineScope by ApplicationThreadPoolScope("RedshiftIamAuth") {
    override fun getId(): String = providerId
    override fun isApplicable(dataSource: LocalDataSource): Boolean = dataSource.dbms.isRedshift
    override fun getDisplayName(): String = message("redshift.auth")

    override fun createWidget(creds: DatabaseCredentials, source: LocalDataSource): AuthWidget? = RedshiftAwsAuthWidget()
    override fun intercept(connection: ProtoConnection, silent: Boolean): CompletionStage<ProtoConnection>? {
        LOG.info { "Intercepting db connection [$connection]" }
        return future {
            val credentials = getCredentials(connection)
            DatabaseCredentialsAuthProvider.applyCredentials(connection, credentials, true)
        }
    }

    private fun getCredentials(connection: ProtoConnection): Credentials? {
        val project = connection.runConfiguration.project
        val credentialManager = CredentialManager.getInstance()
        val clusterIdentifier = connection.connectionPoint.additionalJdbcProperties[CLUSTER_ID_PROPERTY] ?: throw IllegalArgumentException("TODO ")
        val regionId = connection.connectionPoint.additionalJdbcProperties[REGION_ID_PROPERTY]?.let {
            AwsRegionProvider.getInstance().allRegions()[it]
        } ?: throw IllegalArgumentException("TODO ")
        val credentials = connection.connectionPoint.additionalJdbcProperties[CREDENTIAL_ID_PROPERTY]?.let {
            credentialManager.getCredentialIdentifierById(it)?.let {
                credentialManager.getAwsCredentialProvider(it, regionId)
            }
        } ?: throw IllegalArgumentException("TODO ")
        val client: RedshiftClient = project.awsClient(credentials, regionId)
        val username = connection.connectionPoint.dataSource.username
        if (client.describeClusters { it.clusterIdentifier(clusterIdentifier) }.clusters().isEmpty()) {
            throw IllegalArgumentException("LOCALIZE specified cluster does not exist")
        }
        if (username.isEmpty()) {
            throw IllegalArgumentException("LOCALIZE username is empty")
        }
        val creds = client.getClusterCredentials {
            it.clusterIdentifier(clusterIdentifier)
            it.dbUser(username)
            // By default it auto-creates the user if it doesn't exist, which we don't want
            it.autoCreate(false)
        }
        return Credentials(creds.dbUser(), creds.dbPassword())
    }

    companion object {
        const val providerId = "aws.redshift.api"
        private val LOG = getLogger<ApiAuth>()
    }
}
