package software.aws.toolkits.core.credentials

import software.amazon.awssdk.core.auth.AwsCredentials
import software.amazon.awssdk.core.auth.AwsCredentialsProvider
import java.util.concurrent.ConcurrentHashMap

abstract class ToolkitCredentialsProvider(val factory: ToolkitCredentialsProviderFactory) : AwsCredentialsProvider {

    /**
     * The ID should be unique through all the TCPs. It usually takes the concatenation of the type and the name.
     */
    abstract fun id(): String

    /**
     * A user friendly display name shown in the UI.
     */
    abstract fun displayName(): String

    /**
     * Return true if an AWS credentials could be successfully retrieved.
     */
    fun isEnabled(): Boolean = try { credentials; true } catch (e: Exception) { false }

    protected abstract fun getAwsCredentialsProvider(): AwsCredentialsProvider

    override fun getCredentials(): AwsCredentials = getAwsCredentialsProvider().credentials

    abstract fun toMap(): Map<String, String>

    companion object {
        const val P_TYPE = "type"
    }
}

/**
 * The class for managing [ToolkitCredentialsProvider] of the same type.
 * @property type The internal ID for this type of [ToolkitCredentialsProvider], eg 'profile' for AWS account whose credentials is stored in the profile file.
 * @property displayName The name of this account providerFactory.
 * @property description The descriptive information about this account providerFactory.
 */
abstract class ToolkitCredentialsProviderFactory(
        val type: String,
        val displayName: String,
        val description: String
) {
    protected val tcps = ConcurrentHashMap<String, ToolkitCredentialsProvider>()

    abstract fun create(data: Map<String, String>): ToolkitCredentialsProvider?

    fun load(data: Map<String, String>): ToolkitCredentialsProvider? = create(data)?.apply { add(this) }

    fun add(provider: ToolkitCredentialsProvider): ToolkitCredentialsProvider = provider.apply { tcps[provider.id()] = provider }

    fun list(): Collection<ToolkitCredentialsProvider> = tcps.values

    fun get(id: String): ToolkitCredentialsProvider? = tcps[id]

    open fun remove(id: String): ToolkitCredentialsProvider? = tcps.remove(id)
}