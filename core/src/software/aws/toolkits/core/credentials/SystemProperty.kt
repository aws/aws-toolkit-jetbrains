package software.aws.toolkits.core.credentials

import software.amazon.awssdk.core.auth.AwsCredentialsProvider
import software.amazon.awssdk.core.auth.SystemPropertyCredentialsProvider

class SystemPropertyToolkitCredentialsProvider(type: SystemPropertyToolkitCredentialsProviderFactory) : ToolkitCredentialsProvider(type) {

    override fun id(): String = factory.type

    override fun displayName(): String = DISPLAY_NAME

    override fun toMap(): Map<String, String> = mapOf(
            P_TYPE to factory.type
    )

    override fun getAwsCredentialsProvider(): AwsCredentialsProvider = SystemPropertyCredentialsProvider.create()

    companion object {
        const val DISPLAY_NAME = "[syspro]"

        @JvmStatic
        fun fromMap(data: Map<String, String>, factory: SystemPropertyToolkitCredentialsProviderFactory): SystemPropertyToolkitCredentialsProvider? =
                if (data[P_TYPE] == factory.type) SystemPropertyToolkitCredentialsProvider(factory) else null
    }
}

class SystemPropertyToolkitCredentialsProviderFactory() : ToolkitCredentialsProviderFactory(TYPE, NAME, DESCRIPTION) {

    override fun create(data: Map<String, String>): ToolkitCredentialsProvider? = SystemPropertyToolkitCredentialsProvider.fromMap(data, this)

    companion object {
        const val TYPE = "sys"
        const val NAME = "System Property"
        const val DESCRIPTION = "This is System Property based Credentials Provider"
    }
}