package software.aws.toolkits.jetbrains.core

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import software.amazon.awssdk.awscore.client.builder.AwsDefaultClientBuilder
import software.amazon.awssdk.core.SdkClient
import software.amazon.awssdk.core.client.builder.ClientHttpConfiguration
import software.amazon.awssdk.http.apache.ApacheSdkHttpClientFactory
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3ClientBuilder
import software.aws.toolkits.core.ToolkitClientManager
import software.aws.toolkits.core.credentials.ToolkitCredentialsProvider
import software.aws.toolkits.core.region.AwsRegion
import software.aws.toolkits.jetbrains.core.credentials.ProjectAccountSettingsManager
import java.lang.reflect.Modifier
import javax.security.auth.login.CredentialNotFoundException

class AwsClientManager internal constructor(
    project: Project
) : ToolkitClientManager(), Disposable {
    private val accountSettingsManager = ProjectAccountSettingsManager.getInstance(project)
    private val httpClient = ApacheSdkHttpClientFactory.builder().build().createHttpClient()

    init {
        Disposer.register(project, this)
    }

    override fun dispose() {
        cachedClients.values.mapNotNull { it as? AutoCloseable }.forEach { it.close() }
        httpClient.close()
    }

    override fun getCredentialsProvider(): ToolkitCredentialsProvider {
        try {
            return accountSettingsManager.activeCredentialProvider
        } catch (e: CredentialNotFoundException) {
            // TODO: Notify user

            // Throw canceled exception to stop any task relying on this call
            throw ProcessCanceledException(e)
        }
    }

    override fun getRegion(): AwsRegion {
        return accountSettingsManager.activeRegion
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T : SdkClient> createNewClient(key: AwsClientKey): T {
        if (key.region != AwsRegion.GLOBAL && GLOBAL_SERVICES.contains(key.serviceClass.simpleName)) {
            return cachedClients.computeIfAbsent(key.copy(region = AwsRegion.GLOBAL)) { createNewClient(it) } as T
        }

        val builderMethod = key.serviceClass.java.methods.find {
            it.name == "builder" && Modifier.isStatic(it.modifiers) && Modifier.isPublic(it.modifiers)
        } ?: throw IllegalArgumentException("Expected service interface to have a public static `builder()` method.")
        val builder = builderMethod.invoke(null) as AwsDefaultClientBuilder<*, *>

        return builder
            .httpConfiguration(ClientHttpConfiguration.builder().httpClient(httpClient).build())
            .credentialsProvider(getCredentialsProvider())
            .region(Region.of(key.region.id))
            .also {
                if (it is S3ClientBuilder) {
                    it.advancedConfiguration { it.pathStyleAccessEnabled(true) }
                }
            }
            .build() as T
    }

    companion object {
        @JvmStatic
        fun getInstance(project: Project): AwsClientManager {
            return ServiceManager.getService(project, AwsClientManager::class.java)
        }

        private val GLOBAL_SERVICES = setOf("IAMClient")
    }
}