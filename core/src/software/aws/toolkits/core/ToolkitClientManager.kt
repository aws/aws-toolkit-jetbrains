package software.aws.toolkits.core

import software.amazon.awssdk.core.SdkClient
import software.aws.toolkits.core.credentials.ToolkitCredentialsProvider
import software.aws.toolkits.core.region.AwsRegion
import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.KClass

/**
 * An SPI for caching of AWS clients inside of a toolkit
 */
abstract class ToolkitClientManager {
    protected data class AwsClientKey(val profileName: String, val region: AwsRegion, val serviceClass: KClass<out SdkClient>)

    protected val cachedClients = ConcurrentHashMap<AwsClientKey, SdkClient>()

    fun <T : SdkClient> getClient(clz: KClass<T>): T {
        val key = AwsClientKey(
                profileName = getCredentialsProvider().id,
                region = getRegion(),
                serviceClass = clz
        )

        @Suppress("UNCHECKED_CAST")
        return cachedClients.computeIfAbsent(key) { createNewClient(it) } as T
    }

    inline fun <reified T : SdkClient> getClient(): T = this.getClient(T::class)

    /**
     * Get the current active credential provider for the toolkit
     */
    protected abstract fun getCredentialsProvider(): ToolkitCredentialsProvider

    /**
     * Get the current active region for the toolkit
     */
    protected abstract fun getRegion(): AwsRegion

    /**
     * Creates a new client for the requested [AwsClientKey]
     */
    protected abstract fun <T : SdkClient> createNewClient(key: AwsClientKey): T
}