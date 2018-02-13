package software.aws.toolkits.core.credentials

import software.amazon.awssdk.core.auth.AwsCredentialsProvider

/**
 * The immutable Toolkit level AWS Credentials Provider. This is used for the end users to choose from different
 * AWS Credentials Providers in a consistent format.
 */
interface ToolkitCredentialsProvider {
    /**
     * The Toolkit profile name for this Credentials Provider.
     */
    val profileName: String

    /**
     * The factory which contains the factory metadata of this Toolkit Credentials Provider.
     */
    val factory: ToolkitCredentialsProviderFactory

    /**
     * The internal provider ID for this Credentials Provider which is invisible from the end users
     * and can be parsed by [ToolkitCredentialsProviderFactory] to return the corresponding
     * AWS Credentials Provider [AwsCredentialsProvider].
     */
    val id: String

    /**
     * Return the underlying AWS Credentials Provider.
     */
    fun getAwsCredentialsProvider(): AwsCredentialsProvider? {
        return factory.getAwsCredentialsProvider(id)
    }
}