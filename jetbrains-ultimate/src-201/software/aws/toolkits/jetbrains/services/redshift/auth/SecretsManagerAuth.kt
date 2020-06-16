// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.redshift.auth

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.intellij.credentialStore.Credentials
import com.intellij.database.access.DatabaseCredentials
import com.intellij.database.dataSource.DatabaseAuthProvider
import com.intellij.database.dataSource.DatabaseAuthProvider.AuthWidget
import com.intellij.database.dataSource.DatabaseConnectionInterceptor.ProtoConnection
import com.intellij.database.dataSource.DatabaseCredentialsAuthProvider
import com.intellij.database.dataSource.LocalDataSource
import com.intellij.openapi.project.Project
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.future.future
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient
import software.aws.toolkits.core.utils.getLogger
import software.aws.toolkits.core.utils.info
import software.aws.toolkits.jetbrains.core.awsClient
import software.aws.toolkits.jetbrains.core.credentials.ConnectionSettings
import software.aws.toolkits.jetbrains.core.credentials.CredentialManager
import software.aws.toolkits.jetbrains.core.region.AwsRegionProvider
import software.aws.toolkits.jetbrains.ui.CREDENTIAL_ID_PROPERTY
import software.aws.toolkits.jetbrains.ui.REGION_ID_PROPERTY
import software.aws.toolkits.jetbrains.utils.ApplicationThreadPoolScope
import software.aws.toolkits.resources.message
import java.util.concurrent.CompletionStage

data class SecretsManagerConnection(
    val connectionSettings: ConnectionSettings,
    val secretId: String
)

class SecretsManagerAuth : DatabaseAuthProvider, CoroutineScope by ApplicationThreadPoolScope("RedshiftSecretsManagerAuth") {
    // TODO fix this
    private val objectMapper = jacksonObjectMapper().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)

    override fun getId(): String = providerId
    override fun isApplicable(dataSource: LocalDataSource): Boolean = dataSource.dbms.isRedshift
    override fun getDisplayName(): String = message("redshift.auth.secrets_manager")

    override fun createWidget(creds: DatabaseCredentials, source: LocalDataSource): AuthWidget? = SecretsManagerAuthWidget()
    override fun intercept(
        connection: ProtoConnection,
        silent: Boolean
    ): CompletionStage<ProtoConnection>? {
        LOG.info { "Intercepting db connection [$connection]" }
        return future {
            val connectionSettings = getConnectionSettings(connection)
            val credentials = getCredentials(connection.runConfiguration.project, connectionSettings)
            DatabaseCredentialsAuthProvider.applyCredentials(connection, credentials, true)
        }
    }

    private fun getConnectionSettings(connection: ProtoConnection): SecretsManagerConnection {
        val credentialManager = CredentialManager.getInstance()
        val regionId = connection.connectionPoint.additionalJdbcProperties[REGION_ID_PROPERTY]
        val region = regionId?.let {
            AwsRegionProvider.getInstance().allRegions()[it]
        } ?: throw IllegalArgumentException(message("redshift.validation.invalid_region_specified", regionId.toString()))
        val credentialId = connection.connectionPoint.additionalJdbcProperties[CREDENTIAL_ID_PROPERTY]
        val credentials = credentialId?.let { id ->
            credentialManager.getCredentialIdentifierById(id)?.let {
                credentialManager.getAwsCredentialProvider(it, region)
            }
        } ?: throw IllegalArgumentException(message("redshift.validation.invalid_credential_specified", credentialId.toString()))
        val secretId = connection.connectionPoint.additionalJdbcProperties[SECRET_ID_PROPERTY] ?: throw IllegalArgumentException("TODO LOCALIZE")
        return SecretsManagerConnection(
            ConnectionSettings(credentials, region),
            secretId
        )
    }

    // TODO actually make this work
    private fun getCredentials(project: Project, connection: SecretsManagerConnection): Credentials {
        val client = project.awsClient<SecretsManagerClient>(connection.connectionSettings.credentials, connection.connectionSettings.region)
        val secret = client.getSecretValue { it.secretId(connection.secretId) }
        val tree = objectMapper.readTree(secret.secretString())
        return Credentials(tree["username"].asText(), tree["password"].asText())
    }

    companion object {
        const val providerId = "aws.redshift.secretsmanager"
        private val LOG = getLogger<SecretsManagerAuth>()
    }
}
