// Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.core.credentials

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.project.Project
import com.intellij.util.messages.Topic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import software.aws.toolkits.core.credentials.ToolkitCredentialsChangeListener
import software.aws.toolkits.core.credentials.ToolkitCredentialsProvider
import software.aws.toolkits.core.region.AwsRegion
import software.aws.toolkits.jetbrains.core.AwsResourceCache
import software.aws.toolkits.jetbrains.services.sts.StsResources
import software.aws.toolkits.jetbrains.utils.Edt
import software.aws.toolkits.jetbrains.utils.MRUList
import java.util.concurrent.CancellationException
import kotlin.properties.ObservableProperty
import kotlin.reflect.KProperty

abstract class ProjectAccountSettingsManager2(private val project: Project) {
    private val resourceCache = AwsResourceCache.getInstance(project)

    @Transient
    private var validationJob: Job? = null
    @Transient
    protected var connectionState: ConnectionValidationState = ConnectionValidationState.INITIALIZING

    protected val recentlyUsedProfiles = MRUList<ToolkitCredentialsProvider>(MAX_HISTORY)
    protected val recentlyUsedRegions = MRUList<AwsRegion>(MAX_HISTORY)

    protected var connectionSettings: ConnectionSettings by ValidateAndNotify(ConnectionSettings(null, null))
        private set

    init {
        ApplicationManager.getApplication().messageBus.connect(project)
            .subscribe(CredentialManager.CREDENTIALS_CHANGED, object : ToolkitCredentialsChangeListener {
                override fun providerRemoved(providerId: String) {
                    if (connectionSettings.credentials?.id == providerId) {
                        // TODO: Save setting and shortcut to invalid?
                        changeCredentialProvider(null)
                    }
                }
            })
    }

    fun connectionSettings() = connectionSettings.copy()

    fun isValidConnectionSettings(): Boolean = connectionState == ConnectionValidationState.VALID

    fun changeCredentialProvider(credentialsProvider: ToolkitCredentialsProvider?) {
        connectionSettings = connectionSettings.copy(credentials = credentialsProvider)
    }

    fun changeRegion(awsRegion: AwsRegion) {
        connectionSettings = connectionSettings.copy(region = awsRegion)
    }

    /**
     * Returns the list of recently used [AwsRegion]
     */
    fun recentlyUsedRegions(): List<AwsRegion> = recentlyUsedRegions.elements()

    /**
     * Returns the list of recently used [ToolkitCredentialsProvider]
     */
    fun recentlyUsedCredentials(): List<ToolkitCredentialsProvider> = recentlyUsedProfiles.elements()

    private suspend fun validate(credentialsProvider: ToolkitCredentialsProvider, region: AwsRegion) = withContext(Dispatchers.Default) {
        resourceCache.getResource(
            StsResources.ACCOUNT,
            region = region,
            credentialProvider = credentialsProvider,
            useStale = false,
            forceFetch = true
        )
    }

    private fun broadcastChangeEvent(event: ConnectionSettingsChangeEvent) {
        ApplicationManager.getApplication().assertIsDispatchThread()
        if (!project.isDisposed) {
            project.messageBus.syncPublisher(CONNECTION_SETTINGS_CHANGED).settingsChanged(event)
        }
    }

    private inner class ValidateAndNotify<T>(initialValue: T) : ObservableProperty<T>(initialValue) {
        override fun beforeChange(property: KProperty<*>, oldValue: T, newValue: T): Boolean {
            connectionState = ConnectionValidationState.VALIDATING
            broadcastChangeEvent(ConnectionSettingsStateChange(connectionState))

            return true
        }

        override fun afterChange(property: KProperty<*>, oldValue: T, newValue: T) {
            if (oldValue == newValue) {
                return
            }

            val credentialsProvider = connectionSettings.credentials ?: return
            val region = connectionSettings.region ?: return

            validationJob?.cancel(CancellationException("Newer connection settings chosen"))

            validationJob = GlobalScope.launch(Dispatchers.Edt.immediate) {
                try {
                    validate(credentialsProvider, region)
                    connectionState = ConnectionValidationState.VALID
                    broadcastChangeEvent(ValidConnectionSettings(credentialsProvider, region, connectionState))
                } catch (e: Exception) {
                    connectionState = ConnectionValidationState.INVALID
                    broadcastChangeEvent(InvalidConnectionSettings(credentialsProvider, region, e, connectionState))
                }
            }
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

        fun getInstance(project: Project): ProjectAccountSettingsManager =
            ServiceManager.getService(project, ProjectAccountSettingsManager::class.java)

        private const val MAX_HISTORY = 5
    }
}

enum class ConnectionValidationState {
    INITIALIZING,
    VALIDATING,
    INVALID,
    VALID
}

interface ConnectionSettingsChangeNotifier {
    fun settingsChanged(event: ConnectionSettingsChangeEvent)
}

sealed class ConnectionSettingsChangeEvent(val state: ConnectionValidationState) {}
class ConnectionSettingsStateChange(state: ConnectionValidationState) : ConnectionSettingsChangeEvent(state)

class InvalidConnectionSettings(
    val credentialsProvider: ToolkitCredentialsProvider,
    val region: AwsRegion,
    val cause: Exception,
    state: ConnectionValidationState
) : ConnectionSettingsChangeEvent(state)

class ValidConnectionSettings(val credentialsProvider: ToolkitCredentialsProvider, val region: AwsRegion, state: ConnectionValidationState) :
    ConnectionSettingsChangeEvent(state)
