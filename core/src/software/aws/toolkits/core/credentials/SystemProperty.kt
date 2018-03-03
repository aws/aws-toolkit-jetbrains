package software.aws.toolkits.core.credentials

import software.amazon.awssdk.core.auth.AwsCredentials
import software.amazon.awssdk.core.auth.AwsCredentialsProvider
import software.amazon.awssdk.core.auth.SystemPropertyCredentialsProvider
import software.aws.toolkits.core.credentials.SystemPropertyToolkitCredentialsProviderFactory.Companion.TYPE

class SystemPropertyToolkitCredentialsProvider() : ToolkitCredentialsProvider {

    private val awsCredentialsProvider: AwsCredentialsProvider

    init {
        awsCredentialsProvider = SystemPropertyCredentialsProvider.create()
    }

    override fun id(): String = TYPE

    override fun displayName(): String = DISPLAY_NAME

    override fun toMap(): Map<String, String> = mapOf()

    override fun getCredentials(): AwsCredentials = awsCredentialsProvider.credentials

    companion object {
        const val DISPLAY_NAME = "[syspro]"
    }
}

class SystemPropertyToolkitCredentialsProviderFactory() : ToolkitCredentialsProviderFactory(TYPE, NAME, DESCRIPTION) {

    override fun create(data: Map<String, String>): ToolkitCredentialsProvider? = SystemPropertyToolkitCredentialsProvider()

    companion object {
        const val TYPE = "sys"
        const val NAME = "System Property"
        const val DESCRIPTION = "System Property based Credentials Provider"
    }
}