// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.rds.auth
import com.intellij.credentialStore.Credentials
import com.intellij.database.access.DatabaseCredentials
import com.intellij.database.dataSource.DataSourceUiUtil
import com.intellij.database.dataSource.DatabaseAuthProvider
import com.intellij.database.dataSource.DatabaseConnectionInterceptor
import com.intellij.database.dataSource.DatabaseCredentialsAuthProvider
import com.intellij.database.dataSource.LocalDataSource
import com.intellij.database.dataSource.url.JdbcUrlParserUtil
import com.intellij.database.dataSource.url.ui.UrlPropertiesPanel
import com.intellij.ui.components.JBLabel
import com.intellij.uiDesigner.core.GridLayoutManager
import com.intellij.util.text.nullize
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider
import software.amazon.awssdk.auth.signer.Aws4Signer
import software.amazon.awssdk.auth.signer.params.Aws4PresignerParams
import software.amazon.awssdk.http.SdkHttpFullRequest
import software.amazon.awssdk.http.SdkHttpMethod
import software.amazon.awssdk.regions.Region
import software.aws.toolkits.core.region.AwsRegion
import software.aws.toolkits.jetbrains.core.credentials.CredentialManager
import software.aws.toolkits.jetbrains.core.region.AwsRegionProvider
import software.aws.toolkits.jetbrains.ui.CredentialProviderSelector
import java.sql.SQLException
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import javax.swing.JPanel
import javax.swing.event.DocumentListener

class IamAuth : DatabaseAuthProvider {
    override fun getId(): String = "aws.iam"

    override fun isApplicable(dataSource: LocalDataSource): Boolean {
        val dbms = dataSource.dbms
        return dbms.isPostgres || dbms.isMysql
    }

    override fun getDisplayName(): String = "AWS IAM"

    override fun createWidget(credentials: DatabaseCredentials, dataSource: LocalDataSource): DatabaseAuthProvider.AuthWidget? {
        return IamAuthWidget()
    }

    override fun intercept(
        connection: DatabaseConnectionInterceptor.ProtoConnection,
        silent: Boolean
    ): CompletionStage<DatabaseConnectionInterceptor.ProtoConnection>? {
        println("connection = [${connection}], silent = [${silent}]")
        return CompletableFuture.completedFuture(DatabaseCredentialsAuthProvider.applyCredentials(connection, getCredentials(connection), true))
    }

    override fun handleConnectionFailure(
        proto: DatabaseConnectionInterceptor.ProtoConnection,
        e: SQLException,
        silent: Boolean,
        attempt: Int
    ): CompletionStage<DatabaseConnectionInterceptor.ProtoConnection>? {
        println("proto = [${proto}], e = [${e}], silent = [${silent}], attempt = [${attempt}]")
        return super.handleConnectionFailure(proto, e, silent, attempt)
    }

    private fun getCredentials(connection: DatabaseConnectionInterceptor.ProtoConnection): Credentials? {
        val credentialsId = connection.connectionPoint.additionalJdbcProperties[CREDENTIAL_ID_PROPERTY]
            ?: throw IllegalArgumentException("TODO: Credential ID not configured")
        val urlParser = JdbcUrlParserUtil.parsed(connection.connectionPoint.dataSource)
            ?: throw IllegalArgumentException("TODO: Failed to parse URL")
        val host = urlParser.getParameter("host")
            ?: throw IllegalArgumentException("TODO: No host specified")
        val port = urlParser.getParameter("port")?.toInt()
            ?: throw IllegalArgumentException("TODO: No port specified")
        val user = connection.connectionPoint.dataSource.username

        val region = extractRegionFromUrl(host)

        val credentialManager = CredentialManager.getInstance()
        val credentialProviderId = credentialManager.getCredentialIdentifierById(credentialsId)
            ?: throw IllegalArgumentException("TODO: No credentials exist with the ID $credentialsId")
        val credentialProvider = credentialManager.getAwsCredentialProvider(credentialProviderId, region)

        val authToken = generateAuthToken(host, port, user, credentialProvider, region)

        return Credentials(user, authToken)
    }

    private fun generateAuthToken(hostname: String, port: Int, user: String, credentialsProvider: AwsCredentialsProvider, region: AwsRegion): String {
        // TODO: Replace when SDK V2 backfill the pre-signer for rds auth token

        val httpRequest = SdkHttpFullRequest.builder()
            .method(SdkHttpMethod.GET)
            .protocol("https")
            .host(hostname)
            .port(port)
            .encodedPath("/")
            .putRawQueryParameter("DBUser", user)
            .putRawQueryParameter("Action", "connect")
            .build();

        val expirationTime = Instant.now().plus(15, ChronoUnit.MINUTES)
        val presignRequest = Aws4PresignerParams.builder()
            .expirationTime(expirationTime)
            .awsCredentials(credentialsProvider.resolveCredentials())
            .signingName("rds-db")
            .signingRegion(Region.of(region.id))
            .build();

        return Aws4Signer.create().presign(httpRequest, presignRequest).uri.toString().removePrefix("https://")
    }

    private fun extractRegionFromUrl(url: String): AwsRegion {
        // 0 is full match, 1 is the region
        val regionId = RDS_REGION_REGEX.find(url)?.groupValues?.get(1)
            ?: REDSHIFT_REGION_REGEX.find(url)?.groupValues?.get(1)
            ?: throw IllegalStateException("Failed to determine AWS region from '$url'")

        return AwsRegionProvider.getInstance().allRegions()[regionId]
            ?: throw IllegalStateException("No region exists with the ID '$regionId'")
    }

    class IamAuthWidget : DatabaseCredentialsAuthProvider.UserWidget() {
        private val credentialSelector = CredentialProviderSelector()

        override fun createPanel(): JPanel {
            val panel = JPanel(GridLayoutManager(2, 6))
            addUserField(panel, 0)

            val userLabel = JBLabel("Credentials:")
            panel.add(userLabel, UrlPropertiesPanel.createLabelConstraints(1, 0, userLabel.preferredSize.getWidth()))
            panel.add(credentialSelector, UrlPropertiesPanel.createSimpleConstraints(1, 1, 3))

            return panel
        }

        override fun save(dataSource: LocalDataSource, copyCredentials: Boolean) {
            super.save(dataSource, copyCredentials)

            DataSourceUiUtil.putOrRemove(dataSource.additionalJdbcProperties, CREDENTIAL_ID_PROPERTY, credentialSelector.getSelectedCredentialsProvider())
        }

        override fun reset(dataSource: LocalDataSource, resetCredentials: Boolean) {
            super.reset(dataSource, resetCredentials)

            val credentialManager = CredentialManager.getInstance()
            credentialSelector.setCredentialsProviders(credentialManager.getCredentialIdentifiers())

            val credentialId = dataSource.additionalJdbcProperties[CREDENTIAL_ID_PROPERTY]?.nullize()

            credentialId?.let {
                credentialManager.getCredentialIdentifierById(credentialId)?.let {
                    credentialSelector.setSelectedCredentialsProvider(it)
                    return
                }
            }

            credentialSelector.setSelectedInvalidCredentialsProvider(credentialId)
        }

        override fun onChanged(listener: DocumentListener) {
            // TODO: Whats this do? Combo boxes dont have a document listener
        }

        override fun isPasswordChanged(): Boolean = false
    }

    private companion object {
        const val CREDENTIAL_ID_PROPERTY = "AWS.CredentialId"
        val RDS_REGION_REGEX = """.*\.(.+).rds\.""".toRegex()
        val REDSHIFT_REGION_REGEX = """.*\..*\.(.+).redshift\.""".toRegex()
    }
}
