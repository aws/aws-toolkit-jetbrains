package software.aws.toolkits.core.credentials

import software.amazon.awssdk.core.auth.AwsCredentialsProvider
import software.amazon.awssdk.core.auth.EnvironmentVariableCredentialsProvider

class EnvironmentVariableToolkitCredentialsProvider(type: EnvironmentVariableToolkitCredentialsProviderFactory)
    : ToolkitCredentialsProvider(type) {

    override fun toMap(): Map<String, String> = mapOf(
            P_TYPE to factory.type
    )

    /**
     * Uses the factory ID as the ID for the provider as there is only one instance for Environment Variable Credentials Provider
     */
    override fun id(): String = factory.type

    override fun displayName(): String = DISPLAY_NAME

    override fun getAwsCredentialsProvider(): AwsCredentialsProvider = EnvironmentVariableCredentialsProvider.create()

    companion object {
        const val DISPLAY_NAME = "[envvar]"

        @JvmStatic
        fun fromMap(data: Map<String, String>, factory: EnvironmentVariableToolkitCredentialsProviderFactory)
                : EnvironmentVariableToolkitCredentialsProvider? =
                if (data[P_TYPE] == factory.type) EnvironmentVariableToolkitCredentialsProvider(factory) else null
    }
}

class EnvironmentVariableToolkitCredentialsProviderFactory : ToolkitCredentialsProviderFactory(TYPE, NAME, DESCRIPTION) {

    override fun create(data: Map<String, String>): ToolkitCredentialsProvider? = EnvironmentVariableToolkitCredentialsProvider.fromMap(data, this)

    companion object {
        const val TYPE: String = "env"
        const val NAME: String = "Environment Variable"
        const val DESCRIPTION: String = "This is a Credentials Provider by Environment Variables"
    }
}