package software.aws.toolkits.core.credentials

import software.amazon.awssdk.core.auth.AwsCredentialsProvider
import software.amazon.awssdk.core.auth.SystemPropertyCredentialsProvider

/**
 * System property credentials provider type
 */
object ToolkitSystemPropertyCredentialsProviderFactory : ToolkitCredentialsProviderFactory {
    private const val CREDENTIALS_PROVIDER_ID = "__sys"

    override val name: String = "System Properties Credentials Provider"
    override val description: String = "AWS Credentials Provider factory using the system properties"

    private val credentialsProviders = mutableMapOf<String, ToolkitSystemPropertyCredentialsProvider>()

    override fun getAwsCredentialsProvider(providerId: String): AwsCredentialsProvider? {
        return if (CREDENTIALS_PROVIDER_ID == providerId) SystemPropertyCredentialsProvider.create() else null;
    }

    override fun listAwsToolkitCredentialsProviders(): Collection<ToolkitCredentialsProvider> = credentialsProviders.values

    fun create(profileName: String): ToolkitCredentialsProvider {
        val credentialsProvider = ToolkitSystemPropertyCredentialsProvider(profileName)
        credentialsProviders.put(profileName, credentialsProvider)
        return credentialsProvider
    }

    /**
     * Remove the underlying profile with the given profileName
     */
    fun remove(profileName: String) {
        credentialsProviders.remove(profileName)
    }

    private class ToolkitSystemPropertyCredentialsProvider internal constructor(
            override var profileName: String
    ) : ToolkitCredentialsProvider {

        override val factory: ToolkitCredentialsProviderFactory = ToolkitSystemPropertyCredentialsProviderFactory

        override val id: String = CREDENTIALS_PROVIDER_ID
    }
}