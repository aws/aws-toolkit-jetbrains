// Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.core.credentials

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.SimpleModificationTracker
import com.intellij.util.messages.Topic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.TestOnly
import software.aws.toolkits.core.credentials.CredentialProviderNotFound
import software.aws.toolkits.core.credentials.ToolkitCredentialsChangeListener
import software.aws.toolkits.core.credentials.ToolkitCredentialsProvider
import software.aws.toolkits.core.region.AwsRegion
import software.aws.toolkits.core.utils.getLogger
import software.aws.toolkits.core.utils.tryOrNull
import software.aws.toolkits.core.utils.warn
import software.aws.toolkits.jetbrains.core.AwsResourceCache
import software.aws.toolkits.jetbrains.core.region.AwsRegionProvider
import software.aws.toolkits.jetbrains.services.sts.StsResources
import software.aws.toolkits.jetbrains.utils.MRUList
import software.aws.toolkits.resources.message
import java.util.concurrent.CancellationException

abstract class ProjectAccountSettingsManager(private val project: Project) : SimpleModificationTracker() {
    private val resourceCache = AwsResourceCache.getInstance(project)

    @Volatile
    private var validationJob: Job? = null
    @Volatile
    var connectionState: ConnectionState = ConnectionState.INITIALIZING
        @TestOnly get
        protected set

    protected val recentlyUsedProfiles = MRUList<String>(MAX_HISTORY)
    protected val recentlyUsedRegions = MRUList<String>(MAX_HISTORY)

    protected var connectionSettings: ConnectionSettings = ConnectionSettings(null, null)
        protected set(value) {
            incModificationCount()

            connectionState = ConnectionState.VALIDATING
            validationJob?.cancel(CancellationException("Newer connection settings chosen"))

            value.credentials?.let {
                recentlyUsedProfiles.add(it.id)
            }

            value.region?.let {
                recentlyUsedRegions.add(it.id)
            }

            field = value

            validationJob = GlobalScope.launch(Dispatchers.Default) {
                broadcastChangeEvent(ConnectionSettingsStateChange(connectionState))

                val credentials = value.credentials
                val region = value.region
                if (credentials == null || region == null) {
                    connectionState = ConnectionState.INVALID
                    broadcastChangeEvent(ConnectionSettingsStateChange(connectionState))
                    incModificationCount()
                    return@launch
                }

                try {
                    validate(value.credentials, value.region)
                    connectionState = ConnectionState.VALID
                    broadcastChangeEvent(ValidConnectionSettings(credentials, region, connectionState))
                } catch (e: Exception) {
                    connectionState = ConnectionState.INVALID
                    broadcastChangeEvent(InvalidConnectionSettings(credentials, region, e, connectionState))
                } finally {
                    incModificationCount()
                }
            }
        }

    init {
        ApplicationManager.getApplication().messageBus.connect(project)
            .subscribe(CredentialManager.CREDENTIALS_CHANGED, object : ToolkitCredentialsChangeListener {
                override fun providerRemoved(providerId: String) {
                    if (connectionSettings.credentials?.id == providerId) {
                        // Bypass changing MRU list
                        connectionSettings = connectionSettings.copy(credentials = null)
                    }
                }
            })
    }

    fun connectionSettings() = connectionSettings.copy()

    @Deprecated("Use connectionSettings()", replaceWith = ReplaceWith("connectionSettings().region"))
    val activeRegion: AwsRegion
        get() = connectionSettings.region ?: AwsRegionProvider.getInstance().defaultRegion().also {
            LOGGER.warn(IllegalStateException()) { "Using activeRegion when region is null, calling code needs to be migrated to handle null" }
        }

    @Deprecated("Use connectionSettings()", replaceWith = ReplaceWith("connectionSettings().credentials"))
    val activeCredentialProvider: ToolkitCredentialsProvider
        @Throws(CredentialProviderNotFound::class)
        get() = connectionSettings.credentials ?: throw CredentialProviderNotFound(message("credentials.profile.not_configured"))

    @Deprecated("Use isValidConnectionSettings()", replaceWith = ReplaceWith("isValidConnectionSettings()"))
    fun hasActiveCredentials(): Boolean = isValidConnectionSettings()

    fun isValidConnectionSettings(): Boolean = connectionState == ConnectionState.VALID

    // TODO: Make this not null, few tests need to be fixed
    fun changeCredentialProvider(credentialsProvider: ToolkitCredentialsProvider?) {
        connectionSettings = connectionSettings.copy(credentials = credentialsProvider)
    }

    fun changeRegion(awsRegion: AwsRegion) {
        connectionSettings = connectionSettings.copy(region = awsRegion)
    }

    /**
     * Returns the list of recently used [AwsRegion]
     */
    fun recentlyUsedRegions(): List<AwsRegion> {
        val regionProvider = AwsRegionProvider.getInstance()
        return recentlyUsedRegions.elements().mapNotNull { regionProvider.regions()[it] }
    }

    /**
     * Returns the list of recently used [ToolkitCredentialsProvider]
     */
    fun recentlyUsedCredentials(): List<ToolkitCredentialsProvider> {
        val credentialsProvider = CredentialManager.getInstance()
        return recentlyUsedProfiles.elements().mapNotNull { tryOrNull { credentialsProvider.getCredentialProvider(it) } }
    }

    protected open suspend fun validate(credentialsProvider: ToolkitCredentialsProvider, region: AwsRegion) = withContext(Dispatchers.Default) {
        // TODO: Convert the cache over to suspend methods
        resourceCache.getResourceNow(
            StsResources.ACCOUNT,
            region = region,
            credentialProvider = credentialsProvider,
            useStale = false,
            forceFetch = true
        )
        true
    }

    private fun broadcastChangeEvent(event: ConnectionSettingsChangeEvent) {
        if (!project.isDisposed) {
            project.messageBus.syncPublisher(CONNECTION_SETTINGS_CHANGED).settingsChanged(event)
        }
    }

    companion object {
        /***
         * MessageBus topic for when the active credential profile or region is changed
         */
        val CONNECTION_SETTINGS_CHANGED: Topic<ConnectionSettingsChangeNotifier> = Topic.create(
            "AWS Account setting changed",
            ConnectionSettingsChangeNotifier::class.java
        )

        fun getInstance(project: Project): ProjectAccountSettingsManager = ServiceManager.getService(project, ProjectAccountSettingsManager::class.java)

        private val LOGGER = getLogger<DefaultProjectAccountSettingsManager>()
        private const val MAX_HISTORY = 5
    }
}

enum class ConnectionState {
    INITIALIZING,
    VALIDATING,
    INVALID,
    VALID
}

interface ConnectionSettingsChangeNotifier {
    fun settingsChanged(event: ConnectionSettingsChangeEvent)
}

sealed class ConnectionSettingsChangeEvent(val state: ConnectionState)
class ConnectionSettingsStateChange(state: ConnectionState) : ConnectionSettingsChangeEvent(state)

class InvalidConnectionSettings(
    val credentialsProvider: ToolkitCredentialsProvider,
    val region: AwsRegion,
    val cause: Exception,
    state: ConnectionState
) : ConnectionSettingsChangeEvent(state)

class ValidConnectionSettings(val credentialsProvider: ToolkitCredentialsProvider, val region: AwsRegion, state: ConnectionState) :
    ConnectionSettingsChangeEvent(state)

fun Project.activeRegion(): AwsRegion = ProjectAccountSettingsManager.getInstance(this).activeRegion
fun Project.activeCredentialProvider(): ToolkitCredentialsProvider = ProjectAccountSettingsManager.getInstance(this).activeCredentialProvider
/**
 * The underlying AWS account for current active credential provider of the project. Return null if credential provider is not set.
 * Calls of this member should be in non-UI thread since it makes network call using an STS client for retrieving the
 * underlying AWS account.
 */
fun Project.activeAwsAccount(): String? = tryOrNull { AwsResourceCache.getInstance(this).getResourceNow(StsResources.ACCOUNT) }
