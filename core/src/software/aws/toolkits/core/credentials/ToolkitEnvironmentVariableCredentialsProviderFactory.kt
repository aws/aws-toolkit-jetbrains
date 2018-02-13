package software.aws.toolkits.core.credentials

import software.amazon.awssdk.core.auth.AwsCredentialsProvider
import software.amazon.awssdk.core.auth.EnvironmentVariableCredentialsProvider

/**
 * Environment variable credentials provider type
 */
object ToolkitEnvironmentVariableCredentialsProviderFactory : ToolkitCredentialsProviderFactory {
    private const val CREDENTIALS_PROVIDER_ID = "__env"

    override val name: String = "Environment Variables Credentials Provider"
    override val description: String = "AWS Credentials Provider factory using the environment variables"

    private val credentialsProviders = mutableMapOf<String, ToolkitEnvironmentVariableCredentialsProvider>()

    override fun getAwsCredentialsProvider(providerId: String): AwsCredentialsProvider? {
        return if (CREDENTIALS_PROVIDER_ID == providerId) EnvironmentVariableCredentialsProvider.create() else null
    }

    override fun listAwsToolkitCredentialsProviders(): Collection<ToolkitCredentialsProvider> = credentialsProviders.values

    /**
     * Create a new profile using Environment Variable as the AWS Credentials Provider
     */
    fun create(profileName: String): ToolkitCredentialsProvider {
        val credentialsProvider = ToolkitEnvironmentVariableCredentialsProvider(profileName)
        credentialsProviders.put(profileName, credentialsProvider)
        return credentialsProvider
    }

    /**
     * Remove the underlying profile with the given profileName
     */
    fun remove(profileName: String) {
        credentialsProviders.remove(profileName)
    }

    private class ToolkitEnvironmentVariableCredentialsProvider internal constructor (
            override var profileName: String
    ) : ToolkitCredentialsProvider {

        override val id: String = CREDENTIALS_PROVIDER_ID

        override val factory: ToolkitEnvironmentVariableCredentialsProviderFactory = ToolkitEnvironmentVariableCredentialsProviderFactory
    }
}