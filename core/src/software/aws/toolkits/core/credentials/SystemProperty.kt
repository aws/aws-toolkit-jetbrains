package software.aws.toolkits.core.credentials

import software.amazon.awssdk.auth.credentials.AwsCredentials
import software.amazon.awssdk.auth.credentials.SystemPropertyCredentialsProvider
import software.aws.toolkits.core.credentials.SystemPropertyToolkitCredentialsProviderFactory.Companion.TYPE

class SystemPropertyToolkitCredentialsProvider() : ToolkitCredentialsProvider {
    private val awsCredentialsProvider = SystemPropertyCredentialsProvider.create()

    override fun id() = TYPE

    override fun displayName() = DISPLAY_NAME

    override fun getCredentials(): AwsCredentials = awsCredentialsProvider.credentials

    companion object {
        const val DISPLAY_NAME = "System Properties"
    }
}

class SystemPropertyToolkitCredentialsProviderFactory() : ToolkitCredentialsProviderFactory(TYPE) {
    init {
        val credentialsProvider = SystemPropertyToolkitCredentialsProvider()
        try {
            credentialsProvider.credentials
            add(credentialsProvider)
        } catch (_: Exception) {
            // We can't create creds from sys props, so dont add it
        }
    }

    companion object {
        internal const val TYPE = "sysProps"
        private const val NAME = "System Property"
    }
}