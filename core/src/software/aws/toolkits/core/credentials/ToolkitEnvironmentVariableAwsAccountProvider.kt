package software.aws.toolkits.core.credentials

import software.amazon.awssdk.core.auth.AwsCredentialsProvider
import software.amazon.awssdk.core.auth.EnvironmentVariableCredentialsProvider
import software.aws.toolkits.core.credentials.ToolkitAwsAccount.Companion.ACCOUNT_ID
import software.aws.toolkits.core.credentials.ToolkitAwsAccount.Companion.ACCOUNT_NAME
import software.aws.toolkits.core.credentials.ToolkitAwsAccount.Companion.ACCOUNT_PROVIDER_ID
import java.util.*

/**
 * Environment variable based Toolkit AWS provider
 */
class ToolkitEnvironmentVariableAwsAccountProvider() : ToolkitAwsAccountProvider(ID, NAME, DESCRIPTION) {

    companion object {
        const val ID: String = "env"
        const val NAME: String = "Environment Variables Credentials Provider"
        const val DESCRIPTION = "AWS Credentials Provider provider using the environment variables"
    }

    override fun justLoadToolkitAwsAccount(account: Map<String, String>): ToolkitAwsAccount? {
        val accountId = account[ACCOUNT_ID]
        val accountName = account[ACCOUNT_NAME]
        val providerId = account[ACCOUNT_PROVIDER_ID]

        return when {
            accountId == null || accountName == null || providerId == null || providerId != id -> null
            else -> justCreate(accountId, accountName)
        }
    }

    /**
     * Create a new [ToolkitAwsAccount] and manage it.
     */
    fun create(accountName: String): ToolkitAwsAccount =
            justCreate(UUID.randomUUID().toString(), accountName).apply { accounts[this.id] = this }

    /**
     * Just create a new [ToolkitAwsAccount]
     */
    private fun justCreate(accountId: String, accountName: String): ToolkitAwsAccount =
            ToolkitEnvironmentVariableAwsAccount(accountId, accountName, this)

    private class ToolkitEnvironmentVariableAwsAccount(id: String, name: String, provider: ToolkitEnvironmentVariableAwsAccountProvider)
        : ToolkitAwsAccount(id, name, provider) {

        override fun getAwsCredentialsProvider(): AwsCredentialsProvider =
                EnvironmentVariableCredentialsProvider.create()
    }
}