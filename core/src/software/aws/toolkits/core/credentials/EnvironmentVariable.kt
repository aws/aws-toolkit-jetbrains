package software.aws.toolkits.core.credentials

import software.amazon.awssdk.core.auth.AwsCredentials
import software.amazon.awssdk.core.auth.AwsCredentialsProvider
import software.amazon.awssdk.core.auth.EnvironmentVariableCredentialsProvider

class EnvironmentVariableToolkitCredentialsProvider(private val factory: EnvironmentVariableToolkitCredentialsProviderFactory) : ToolkitCredentialsProvider {

    private val awsCredentialsProvider: AwsCredentialsProvider

    init {
        this.awsCredentialsProvider = EnvironmentVariableCredentialsProvider.create()
    }

    override fun toMap(): Map<String, String> = mapOf()

    /**
     * Uses the factory ID as the ID for the provider as there is only one instance for Environment Variable Credentials Provider
     */
    override fun id(): String = factory.type

    override fun displayName(): String = DISPLAY_NAME

    override fun getCredentials(): AwsCredentials = awsCredentialsProvider.credentials

    companion object {
        const val DISPLAY_NAME = "[envvar]"
    }
}

class EnvironmentVariableToolkitCredentialsProviderFactory : ToolkitCredentialsProviderFactory(TYPE, NAME, DESCRIPTION) {

    override fun create(data: Map<String, String>): ToolkitCredentialsProvider? =
            EnvironmentVariableToolkitCredentialsProvider(this)

    companion object {
        const val TYPE: String = "env"
        const val NAME: String = "Environment Variable"
        const val DESCRIPTION: String = "Environment Variables based Credentials Provider"
    }
}