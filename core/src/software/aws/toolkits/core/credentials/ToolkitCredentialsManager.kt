package software.aws.toolkits.core.credentials

import software.amazon.awssdk.core.auth.AwsCredentialsProvider
import java.util.concurrent.CopyOnWriteArrayList

/**
 * The central hub for managing AWS profiles, ie. [AwsCredentialsProvider].
 */
object ToolkitCredentialsManager {

    private val factories = CopyOnWriteArrayList<ToolkitCredentialsProviderFactory>()

    /**
     * Return the corresponding [AwsCredentialsProvider] based on the provided provider ID. It iterates all the
     * registered [ToolkitCredentialsProviderFactory] to handle this provider ID
     *
     * @return The [AwsCredentialsProvider] if either factory could handle it, null otherwise
     */
    fun findAwsCredentialsProvider(providerId: String): AwsCredentialsProvider? {
        return factories.mapNotNull { it.getAwsCredentialsProvider(providerId) }.firstOrNull()
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
    fun listRegisteredCredentialsProviderFactories(): Collection<ToolkitCredentialsProviderFactory> {
        return factories
    }

    /**
     * Clean all the registered factories.
     */
    fun reset() {
        factories.clear()
    }
}