// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.rds.auth

import com.intellij.credentialStore.Credentials
import com.intellij.database.Dbms
import com.intellij.database.access.DatabaseCredentials
import com.intellij.database.dataSource.DatabaseAuthProvider
import com.intellij.database.dataSource.DatabaseAuthProvider.AuthWidget
import com.intellij.database.dataSource.DatabaseConnectionInterceptor.ProtoConnection
import com.intellij.database.dataSource.DatabaseCredentialsAuthProvider
import com.intellij.database.dataSource.LocalDataSource
import com.intellij.database.dataSource.url.JdbcUrlParserUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.future.future
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider
import software.amazon.awssdk.auth.signer.Aws4Signer
import software.amazon.awssdk.auth.signer.params.Aws4PresignerParams
import software.amazon.awssdk.http.SdkHttpFullRequest
import software.amazon.awssdk.http.SdkHttpMethod
import software.amazon.awssdk.regions.Region
import software.aws.toolkits.core.region.AwsRegion
import software.aws.toolkits.core.utils.error
import software.aws.toolkits.core.utils.getLogger
import software.aws.toolkits.core.utils.info
import software.aws.toolkits.jetbrains.core.credentials.CredentialManager
import software.aws.toolkits.jetbrains.core.help.HelpIds
import software.aws.toolkits.jetbrains.core.region.AwsRegionProvider
import software.aws.toolkits.jetbrains.utils.ApplicationThreadPoolScope
import software.aws.toolkits.jetbrains.utils.actions.OpenBrowserAction
import software.aws.toolkits.jetbrains.utils.notifyError
import software.aws.toolkits.resources.message
import java.sql.SQLException
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.concurrent.CompletionStage

data class RdsAuthInformation(
    val hostname: String,
    val port: Int,
    val user: String,
    val credentialProvider: AwsCredentialsProvider,
    val region: AwsRegion
)

// This is marked as internal but is what we were told to use
class IamAuth : DatabaseAuthProvider, CoroutineScope by ApplicationThreadPoolScope("RdsIamAuth") {
    override fun getId(): String = providerId
    override fun getDisplayName(): String = message("rds.iam_connection_display_name")

    override fun isApplicable(dataSource: LocalDataSource): Boolean = dataSource.dbms == Dbms.MYSQL || dataSource.dbms == Dbms.POSTGRES

    override fun createWidget(credentials: DatabaseCredentials, dataSource: LocalDataSource): AuthWidget? = IamAuthWidget()

    override fun intercept(
        connection: ProtoConnection,
        silent: Boolean
    ): CompletionStage<ProtoConnection>? {
        LOG.info { "Intercepting db connection [$connection]" }
        return future {
            val credentials = try {
                getCredentials(connection)
            } catch (e: Exception) {
                LOG.error(e) { "An exception was thrown creating the db credentials" }
                notifyError(title = message("rds.validation.failed"), content = e.message ?: "")
                null
            }
            DatabaseCredentialsAuthProvider.applyCredentials(connection, credentials, true)
        }
    }

    override fun handleConnectionFailure(
        proto: ProtoConnection,
        e: SQLException,
        silent: Boolean,
        attempt: Int
    ): CompletionStage<ProtoConnection>? {
        LOG.error(e) { "proto = [$proto], silent = [$silent], attempt = [$attempt]" }
        notifyError(
            title = message("aws.notification.title"),
            content = message("rds.connection_Failed"),
            action = OpenBrowserAction(message("rds.validation.setup_guide"), null, HelpIds.RDS_SETUP_IAM_AUTH.url)
        )
        // return null because we don't attempt to do anything further
        return null
    }

    private fun getCredentials(connection: ProtoConnection): Credentials? {
        val authInformation = validateConnection(connection)
        val authToken = generateAuthToken(authInformation)
        return Credentials(authInformation.user, authToken)
    }

    internal fun validateConnection(connection: ProtoConnection): RdsAuthInformation {
        val regionId = connection.connectionPoint.additionalJdbcProperties[REGION_ID_PROPERTY]
            ?: throw IllegalArgumentException(message("rds.validation.no_region_specified"))
        val credentialsId = connection.connectionPoint.additionalJdbcProperties[CREDENTIAL_ID_PROPERTY]
            ?: throw IllegalArgumentException(message("rds.validation.no_profile_selected"))
        val parsedUrl = JdbcUrlParserUtil.parsed(connection.connectionPoint.dataSource)
            ?: throw IllegalArgumentException(message("rds.validation.failed_to_parse_url"))
        val host = parsedUrl.getParameter("host")
            ?: throw IllegalArgumentException(message("rds.validation.no_host_specified"))
        val port = parsedUrl.getParameter("port")?.toInt()
            ?: throw IllegalArgumentException(message("rds.validation.no_port_specified"))
        val user = connection.connectionPoint.dataSource.username

        if (user.isBlank()) {
            throw IllegalArgumentException(message("rds.validation.username"))
        }

        val region = AwsRegionProvider.getInstance().allRegions()[regionId]
            ?: throw IllegalArgumentException(message("rds.validation.invalid_region_specified", regionId))

        val credentialManager = CredentialManager.getInstance()
        val credentialProviderId = credentialManager.getCredentialIdentifierById(credentialsId)
            ?: throw IllegalArgumentException(message("rds.validation.invalid_credential_specified", credentialsId))
        val credentialProvider = credentialManager.getAwsCredentialProvider(credentialProviderId, region)

        return RdsAuthInformation(
            host,
            port,
            user,
            credentialProvider,
            region
        )
    }

    private fun generateAuthToken(authInformation: RdsAuthInformation): String {
        // TODO: Replace when SDK V2 backfills the pre-signer for rds auth token
        val httpRequest = SdkHttpFullRequest.builder()
            .method(SdkHttpMethod.GET)
            .protocol("https")
            .host(authInformation.hostname)
            .port(authInformation.port)
            .encodedPath("/")
            .putRawQueryParameter("DBUser", authInformation.user)
            .putRawQueryParameter("Action", "connect")
            .build()

        val expirationTime = Instant.now().plus(15, ChronoUnit.MINUTES)
        val presignRequest = Aws4PresignerParams.builder()
            .expirationTime(expirationTime)
            .awsCredentials(authInformation.credentialProvider.resolveCredentials())
            .signingName("rds-db")
            .signingRegion(Region.of(authInformation.region.id))
            .build()

        return Aws4Signer.create().presign(httpRequest, presignRequest).uri.toString().removePrefix("https://")
    }

    companion object {
        const val providerId = "aws.rds.iam"
        private val LOG = getLogger<IamAuth>()
    }
}
