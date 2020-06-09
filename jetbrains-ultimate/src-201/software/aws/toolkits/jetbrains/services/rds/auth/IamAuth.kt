// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.rds.auth

import com.intellij.credentialStore.Credentials
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
import software.aws.toolkits.jetbrains.core.region.AwsRegionProvider
import software.aws.toolkits.jetbrains.utils.ApplicationThreadPoolScope
import software.aws.toolkits.resources.message
import java.sql.SQLException
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.concurrent.CompletionStage

// This is marked as internal but is what we were told to use
class IamAuth : DatabaseAuthProvider, CoroutineScope by ApplicationThreadPoolScope("IamAuth") {
    override fun getId(): String = providerId

    override fun isApplicable(dataSource: LocalDataSource): Boolean {
        val dbms = dataSource.dbms
        // Postgres also picks up redshift which has different auth so ignore it
        return (dbms.isMysql || dbms.isPostgres) && !dbms.isRedshift
    }

    override fun getDisplayName(): String = message("rds.iam_connection_display_name")

    override fun createWidget(credentials: DatabaseCredentials, dataSource: LocalDataSource): AuthWidget? =
        IamAuthWidget()

    override fun intercept(
        connection: ProtoConnection,
        silent: Boolean
    ): CompletionStage<ProtoConnection>? {
        LOG.info { "Intercepting db connection [$connection]" }
        return future {
            val credentials = getCredentials(connection)
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
        return super.handleConnectionFailure(proto, e, silent, attempt)
    }

    private fun getCredentials(connection: ProtoConnection): Credentials? {
        val regionId = connection.connectionPoint.additionalJdbcProperties[REGION_ID_PROPERTY]
        val credentialsId = connection.connectionPoint.additionalJdbcProperties[CREDENTIAL_ID_PROPERTY]
            ?: throw IllegalArgumentException(message("rds.validation.no_profile_selected"))
        val urlParser = JdbcUrlParserUtil.parsed(connection.connectionPoint.dataSource)
            ?: throw IllegalArgumentException(message("rds.validation.failed_to_parse_url"))
        val host = urlParser.getParameter("host")
            ?: throw IllegalArgumentException(message("rds.validation.no_host_specified"))
        val port = urlParser.getParameter("port")?.toInt()
            ?: throw IllegalArgumentException(message("rds.validation.no_port_specified"))
        val user = connection.connectionPoint.dataSource.username

        val region = extractRegionFromUrl(host) ?: regionId ?: throw IllegalArgumentException(message("rds.validation.no_region_specified"))
        val awsRegion = AwsRegionProvider.getInstance().allRegions()[region]
            ?: throw IllegalArgumentException(message("rds.validation.invalid_region_specified", region))

        val credentialManager = CredentialManager.getInstance()
        val credentialProviderId = credentialManager.getCredentialIdentifierById(credentialsId)
            ?: throw IllegalArgumentException(message("rds.validation.invalid_credential_specified", credentialsId))
        val credentialProvider = credentialManager.getAwsCredentialProvider(credentialProviderId, awsRegion)

        val authToken = generateAuthToken(host, port, user, credentialProvider, awsRegion)

        return Credentials(user, authToken)
    }

    private fun generateAuthToken(hostname: String, port: Int, user: String, credentialsProvider: AwsCredentialsProvider, region: AwsRegion): String {
        // TODO: Replace when SDK V2 backfills the pre-signer for rds auth token
        val httpRequest = SdkHttpFullRequest.builder()
            .method(SdkHttpMethod.GET)
            .protocol("https")
            .host(hostname)
            .port(port)
            .encodedPath("/")
            .putRawQueryParameter("DBUser", user)
            .putRawQueryParameter("Action", "connect")
            .build()

        val expirationTime = Instant.now().plus(15, ChronoUnit.MINUTES)
        val presignRequest = Aws4PresignerParams.builder()
            .expirationTime(expirationTime)
            .awsCredentials(credentialsProvider.resolveCredentials())
            .signingName("rds-db")
            .signingRegion(Region.of(region.id))
            .build()

        return Aws4Signer.create().presign(httpRequest, presignRequest).uri.toString().removePrefix("https://")
    }

    private fun extractRegionFromUrl(url: String): String? = RDS_REGION_REGEX.find(url)?.groupValues?.get(1)

    companion object {
        const val providerId = "aws.iam"
        private val LOG = getLogger<IamAuth>()
        private val RDS_REGION_REGEX = """.*\.(.+).rds\.""".toRegex()
    }
}
