package software.aws.toolkits.jetbrains.core

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import software.amazon.awssdk.core.SdkClient
import software.aws.toolkits.core.ToolkitClientManager
import software.aws.toolkits.core.credentials.ToolkitCredentialsProvider
import software.aws.toolkits.core.region.AwsRegion
import software.aws.toolkits.jetbrains.core.credentials.ProjectAccountSettingsManager
import javax.security.auth.login.CredentialNotFoundException
import kotlin.reflect.KClass

class AwsClientManager internal constructor(
    project: Project
) : ToolkitClientManager(), Disposable {
    private val accountSettingsManager = ProjectAccountSettingsManager.getInstance(project)

    init {
        Disposer.register(project, this)
    }

    override fun dispose() {
        shutdown()
    }

    inline fun <reified T : SdkClient> getClient(): T = this.getClient(T::class)

    fun <T : SdkClient> getClient(clz: KClass<T>): T {
        return getClient(clz, getCredentialsProvider(), getRegion())
    }

    private fun getCredentialsProvider(): ToolkitCredentialsProvider {
        try {
            return accountSettingsManager.activeCredentialProvider
        } catch (e: CredentialNotFoundException) {
            // TODO: Notify user

            // Throw canceled exception to stop any task relying on this call
            throw ProcessCanceledException(e)
        }
    }

    private fun getRegion(): AwsRegion {
        return accountSettingsManager.activeRegion
    }

    companion object {
        @JvmStatic
        fun getInstance(project: Project): AwsClientManager {
            return ServiceManager.getService(project, AwsClientManager::class.java)
        }
    }
}