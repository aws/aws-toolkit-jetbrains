package software.aws.toolkits.core

import software.amazon.awssdk.awscore.client.builder.AwsDefaultClientBuilder
import software.amazon.awssdk.core.SdkClient
import software.amazon.awssdk.core.client.builder.ClientHttpConfiguration
import software.amazon.awssdk.http.apache.ApacheSdkHttpClientFactory
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3ClientBuilder
import software.aws.toolkits.core.credentials.ToolkitCredentialsProvider
import software.aws.toolkits.core.region.AwsRegion
import java.lang.reflect.Modifier
import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.KClass

/**
 * An SPI for caching of AWS clients inside of a toolkit
 */
open class ToolkitClientManager {
    protected data class AwsClientKey(
        val credProvider: ToolkitCredentialsProvider,
        val region: AwsRegion,
        val serviceClass: KClass<out SdkClient>
    )

    private val cachedClients = ConcurrentHashMap<AwsClientKey, SdkClient>()
    private val httpClient = ApacheSdkHttpClientFactory.builder().build().createHttpClient()

    @Suppress("UNCHECKED_CAST")
    open fun <T : SdkClient> getClient(
        clz: KClass<T>,
        credentialsProvider: ToolkitCredentialsProvider,
        region: AwsRegion
    ): T {
        val key = createCacheKey(credentialsProvider, region, clz)

        if (key.region != AwsRegion.GLOBAL && GLOBAL_SERVICES.contains(key.serviceClass.simpleName)) {
            return cachedClients.computeIfAbsent(key.copy(region = AwsRegion.GLOBAL)) { createNewClient(it) } as T
        }

        return cachedClients.computeIfAbsent(key) { createNewClient(it) } as T
    }

    private fun <T : SdkClient> createCacheKey(
        credProvider: ToolkitCredentialsProvider,
        region: AwsRegion,
        clz: KClass<T>
    ): AwsClientKey {
        return AwsClientKey(
            credProvider = credProvider,
            region = region,
            serviceClass = clz
        )
    }

    inline fun <reified T : SdkClient> getClient(credProvider: ToolkitCredentialsProvider, region: AwsRegion): T =
        this.getClient(T::class, credProvider, region)

    /**
     * Calls [AutoCloseable.close] if client implements [AutoCloseable] and clears the cache
     */
    protected fun shutdown() {
        cachedClients.values.mapNotNull { it as? AutoCloseable }.forEach { it.close() }
        httpClient.close()
    }

    /**
     * Creates a new client for the requested [AwsClientKey]
     */
    @Suppress("UNCHECKED_CAST")
    protected open fun <T : SdkClient> createNewClient(key: AwsClientKey): T {
        val builderMethod = key.serviceClass.java.methods.find {
            it.name == "builder" && Modifier.isStatic(it.modifiers) && Modifier.isPublic(it.modifiers)
        } ?: throw IllegalArgumentException("Expected service interface to have a public static `builder()` method.")
        val builder = builderMethod.invoke(null) as AwsDefaultClientBuilder<*, *>

        return builder
            .httpConfiguration(ClientHttpConfiguration.builder().httpClient(httpClient).build())
            .credentialsProvider(key.credProvider)
            .region(Region.of(key.region.id))
            .also {
                if (it is S3ClientBuilder) {
                    it.advancedConfiguration { it.pathStyleAccessEnabled(true) }
                }
            }
            .build() as T
    }

    companion object {
        private val GLOBAL_SERVICES = setOf("IAMClient")
    }
}