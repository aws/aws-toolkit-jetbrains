package software.aws.toolkits.core.credentials

import software.amazon.awssdk.core.auth.AwsCredentialsProvider

/**
 * The central hub for managing AWS profiles, ie. [AwsCredentialsProvider].
 */
object ToolkitCredentialsManager {

    private val factories = mutableSetOf<ToolkitCredentialsProviderFactory>()

    /**
     * Return the corresponding [AwsCredentialsProvider] based on the provided provider ID. It iterates all the
     * registered [ToolkitCredentialsProviderFactory] to handle this provider ID
     *
     * @return The [AwsCredentialsProvider] if either factory could handle it, null otherwise
     */
    fun findAwsCredentialsProvider(providerId: String): AwsCredentialsProvider? {
        var credentialsProvider: AwsCredentialsProvider?

        for (factory in factories) {
            credentialsProvider = factory.getAwsCredentialsProvider(providerId)
            if (credentialsProvider != null) {
                return credentialsProvider
            }
        }
        return null
    }

    /**
     * Register a new [ToolkitCredentialsProviderFactory] in the manager for handling a different type of AWS Credentials Provider
     */
    fun register(factory: ToolkitCredentialsProviderFactory) {
        factories.add(factory)
    }

    /**
     * List all the registered factories in an immutable way
     */
    fun listRegisteredCredentialsProviderFactories(): Set<ToolkitCredentialsProviderFactory> {
        return factories
    }

    /**
     * Clean all the registered factories.
     */
    fun reset() {
        factories.clear()
    }
}