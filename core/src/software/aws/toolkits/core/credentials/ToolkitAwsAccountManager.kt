package software.aws.toolkits.core.credentials

import software.amazon.awssdk.core.auth.AwsCredentialsProvider
import java.util.concurrent.ConcurrentHashMap

/**
 * The central hub for managing Toolkit AWS accounts, ie. [ToolkitAwsAccount].
 */
class ToolkitAwsAccountManager {

    private val providers = ConcurrentHashMap<String, ToolkitAwsAccountProvider>()

    /**
     * Register a new [ToolkitAwsAccountProvider] to the account manager for handling a different type of [AwsCredentialsProvider]
     */
    fun register(provider: ToolkitAwsAccountProvider) {
        providers[provider.id] = provider
    }

    /**
     * List all the registered [ToolkitAwsAccountProvider] in an immutable way
     */
    fun listToolkitAwsAccountProviders(): Collection<ToolkitAwsAccountProvider> =
            providers.values

    /**
     * Return the corresponding [AwsCredentialsProvider] based on the provided AWS account ID. It iterates all the
     * registered [ToolkitAwsAccountProvider] to handle this account ID
     *
     * @return The [AwsCredentialsProvider] if either account provider could handle it, null otherwise
     */
    fun findAwsCredentialsProvider(accountId: String): AwsCredentialsProvider? =
            providers.mapNotNull { it.value.getAwsCredentialsProvider(accountId) }.firstOrNull()

    /**
     * Load a Toolkit AWS account from the persistent data, store it to the underlying [ToolkitAwsAccountProvider], and return it;
     * If neither account provider can parse it, return null.
     */
    fun loadToolkitAwsAccount(persistentData: Map<String, String>): ToolkitAwsAccount? =
            providers.mapNotNull { it.value.loadAndStoreToolkitAwsAccount(persistentData) }.firstOrNull()

    /**
     * Clean all the registered providers.
     */
    fun reset() {
        providers.clear()
    }
}