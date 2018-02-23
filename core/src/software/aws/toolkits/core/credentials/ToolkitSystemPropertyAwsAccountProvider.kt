package software.aws.toolkits.core.credentials

import software.amazon.awssdk.core.auth.AwsCredentialsProvider
import software.amazon.awssdk.core.auth.SystemPropertyCredentialsProvider
import software.aws.toolkits.core.credentials.ToolkitAwsAccount.Companion.ACCOUNT_ID
import software.aws.toolkits.core.credentials.ToolkitAwsAccount.Companion.ACCOUNT_NAME
import software.aws.toolkits.core.credentials.ToolkitAwsAccount.Companion.ACCOUNT_PROVIDER_ID
import java.util.*

/**
 * System property based Toolkit AWS account provider
 */
class ToolkitSystemPropertyAwsAccountProvider() : ToolkitAwsAccountProvider(ID, NAME, DESCRIPTION) {

    companion object {
        const val ID = "sys"
        const val NAME = "System Properties"
        const val DESCRIPTION = "Toolkit AWS account using system properties for the credentials"
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
     * Create a new [ToolkitAwsAccount] and manage it
     */
    fun create(accountName: String): ToolkitAwsAccount =
            justCreate(UUID.randomUUID().toString(), accountName).apply { accounts[this.id] = this }

    /**
     * Just create a new [ToolkitAwsAccount] and return it.
     */
    private fun justCreate(accountId: String, accountName: String): ToolkitAwsAccount =
            ToolkitSystemPropertyAwsAccount(accountId, accountName, this)

    private class ToolkitSystemPropertyAwsAccount(id: String, name: String, provider: ToolkitSystemPropertyAwsAccountProvider)
        : ToolkitAwsAccount(id, name, provider) {

        override fun getAwsCredentialsProvider(): AwsCredentialsProvider =
                SystemPropertyCredentialsProvider.create()

    }
}