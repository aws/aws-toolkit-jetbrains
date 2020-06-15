// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.redshift.auth

import com.intellij.database.access.DatabaseCredentials
import com.intellij.database.dataSource.DatabaseAuthProvider
import com.intellij.database.dataSource.DatabaseConnectionInterceptor
import com.intellij.database.dataSource.DatabaseCredentialsAuthProvider
import com.intellij.database.dataSource.LocalDataSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.future.future
import software.amazon.awssdk.services.redshift.RedshiftClient
import software.aws.toolkits.core.utils.getLogger
import software.aws.toolkits.core.utils.info
import software.aws.toolkits.jetbrains.core.awsClient
import software.aws.toolkits.jetbrains.utils.ApplicationThreadPoolScope
import software.aws.toolkits.resources.message
import java.util.concurrent.CompletionStage

class SecretsManagerAuth : DatabaseAuthProvider, CoroutineScope by ApplicationThreadPoolScope("RedshiftSecretsManagerAuth") {
    override fun getId(): String = providerId
    override fun isApplicable(dataSource: LocalDataSource): Boolean = dataSource.dbms.isRedshift
    override fun getDisplayName(): String = message("redshift.auth.secrets_manager")

    override fun createWidget(creds: DatabaseCredentials, source: LocalDataSource): DatabaseAuthProvider.AuthWidget? = RedshiftAwsAuthWidget()
    override fun intercept(
        connection: DatabaseConnectionInterceptor.ProtoConnection,
        silent: Boolean
    ): CompletionStage<DatabaseConnectionInterceptor.ProtoConnection>? {
        LOG.info { "Intercepting db connection [$connection]" }
        return future {
            val project = connection.runConfiguration.project
            val auth = validateConnection(connection)
            val client = project.awsClient<RedshiftClient>(auth.credentials, auth.region)
            val credentials = getCredentials(auth, client)
            DatabaseCredentialsAuthProvider.applyCredentials(connection, credentials, true)
        }
    }

    companion object {
        const val providerId = "aws.redshift.secretsmanager"
        private val LOG = getLogger<SecretsManagerAuth>()
    }
}
