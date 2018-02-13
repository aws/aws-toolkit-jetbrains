package software.aws.toolkits.core.credentials

import software.amazon.awssdk.core.auth.AwsCredentialsProvider
import java.util.concurrent.ConcurrentHashMap

/**
 * The class for managing [ToolkitAwsAccount] of the same type.
 */
abstract class ToolkitAwsAccountProvider protected constructor(
        /**
         * Return the internal ID for this type of [ToolkitAwsAccount], eg 'profile' for AWS account whose credentials is stored in the profile file.
         */
        val id: String,
        /**
         * Return the name of the Toolkit Credentials Provider Factory.
         */
        val name: String,
        /**
         * Return the descriptive information about the Toolkit Credentials Provider Factory.
         */
        val description: String
) {
    protected val accounts = ConcurrentHashMap<String, ToolkitAwsAccount>()

    /**
     * Return the underlying [AwsCredentialsProvider] based on the given account ID or null if not found
     * @see ToolkitAwsAccount.id
     */
    fun getAwsCredentialsProvider(accountId: String): AwsCredentialsProvider? =
            accounts[accountId]?.getAwsCredentialsProvider()

    /**
     * Load a [ToolkitAwsAccount] from the persistent data and return it.
     * @see ToolkitAwsAccount.persistToMap
     */
    protected abstract fun justLoadToolkitAwsAccount(account: Map<String, String>): ToolkitAwsAccount?

    fun loadAndStoreToolkitAwsAccount(account: Map<String, String>): ToolkitAwsAccount? =
            justLoadToolkitAwsAccount(account)?.apply {
                accounts[this.id] = this
            }

    /**
     * Return all the Toolkit Credentials Providers in this provider.
     */
    fun listToolkitAwsAccount(): Collection<ToolkitAwsAccount> =
            accounts.values

    fun getToolkitAwsAccount(id: String): ToolkitAwsAccount? = accounts[id]

    fun getToolkitAwsAccountByName(name: String): ToolkitAwsAccount? =
            accounts.values.first { it.name == name }

    /**
     * Remove the [ToolkitAwsAccount] with the specified account id from the account Map. Subclass must override this method
     * if it has extra logic for removing an account.
     */
    open fun remove(accountId: String) {
        accounts.remove(accountId)
    }
}