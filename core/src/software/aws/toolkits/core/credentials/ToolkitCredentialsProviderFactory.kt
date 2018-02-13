package software.aws.toolkits.core.credentials

import software.amazon.awssdk.core.auth.AwsCredentialsProvider

/**
 * The factory class for managing Toolkit Credentials Providers of the same type. It can parse the
 * internal ID [ToolkitCredentialsProvider.id] and return the corresponding [AwsCredentialsProvider]
 */
interface ToolkitCredentialsProviderFactory {
    /**
     * Return the name of the Toolkit Credentials Provider Factory.
     */
    val name: String

    /**
     * Return the descriptive information about the Toolkit Credentials Provider Factory.
     */
    val description: String

    /**
     * Return the underlying [AwsCredentialsProvider] based on the given provider ID.
     * @see [ToolkitCredentialsProvider.id]
     */
    fun getAwsCredentialsProvider(providerId: String): AwsCredentialsProvider?

    /**
     * Return all the Toolkit Credentials Providers in this factory.
     */
    fun listAwsToolkitCredentialsProviders(): Collection<ToolkitCredentialsProvider>
}