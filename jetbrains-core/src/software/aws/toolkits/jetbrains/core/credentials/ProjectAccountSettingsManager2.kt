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
import software.aws.toolkits.core.credentials.CredentialProviderNotFound
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
    private var state: ConnectionState = ConnectionState.INITIALIZING

    private val recentlyUsedProfiles = MRUList<ToolkitCredentialsProvider>(MAX_HISTORY)
    private val recentlyUsedRegions = MRUList<AwsRegion>(MAX_HISTORY)

    /**
     * Setting the active region will add to the recently used list, and evict the least recently used if at max size
     */
    private var activeRegionInternal: AwsRegion? by ValidateAndNotify(null, recentlyUsedRegions)

    /**
     * Setting the active provider will add to the recently used list, and evict the least recently used if at max size
     */
    private var activeCredentialProviderInternal: ToolkitCredentialsProvider? by ValidateAndNotify(null, recentlyUsedProfiles)

    var activeRegion: AwsRegion
        get() = activeRegionInternal!! // TODO
        set(value) {
            activeRegionInternal = value
        }

    /**
     * Setting the active provider will add to the recently used list, and evict the least recently used if at max size
     */
    var activeCredentialProvider: ToolkitCredentialsProvider
        @Throws(CredentialProviderNotFound::class)
        get() = activeCredentialProviderInternal!! // TODO
        set(value) {
            activeCredentialProviderInternal = value
        }

    fun hasValidConnectionSettings(): Boolean = state == ConnectionState.VALID

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
            project.messageBus.syncPublisher(ACCOUNT_SETTINGS_CHANGED).settingsChanged(event)
        }
    }

    private inner class ValidateAndNotify<T>(
        initialValue: T?,
        private val recentlyUsed: MRUList<T>
    ) : ObservableProperty<T?>(initialValue) {
        override fun beforeChange(property: KProperty<*>, oldValue: T?, newValue: T?): Boolean {
            state = ConnectionState.VALIDATING
            broadcastChangeEvent(ConnectionSettingsStateChange(state))

            newValue?.let {
                recentlyUsed.add(newValue)
            }

            return true
        }

        override fun afterChange(property: KProperty<*>, oldValue: T?, newValue: T?) {
            if (oldValue == newValue) {
                return
            }

            val credentialsProvider = activeCredentialProviderInternal ?: return
            val region = activeRegionInternal ?: return

            validationJob?.cancel(CancellationException("Newer connection settings chosen"))

            validationJob = GlobalScope.launch(Dispatchers.Edt.immediate) {
                try {
                    validate(credentialsProvider, region)
                    state = ConnectionState.VALID
                    broadcastChangeEvent(ValidConnectionSettings(activeCredentialProvider, activeRegion, state))
                } catch (e: Exception) {
                    state = ConnectionState.INVALID
                    broadcastChangeEvent(InvalidConnectionSettings(activeCredentialProvider, activeRegion, e, state))
                }
            }
        }
    }

    companion object {
        /***
         * MessageBus topic for when the active credential profile or region is changed
         */
        val ACCOUNT_SETTINGS_CHANGED: Topic<ConnectionSettingsChangeNotifier> = Topic.create(
                "AWS Account setting changed",
                ConnectionSettingsChangeNotifier::class.java
            )

        fun getInstance(project: Project): ProjectAccountSettingsManager =
            ServiceManager.getService(project, ProjectAccountSettingsManager::class.java)

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

sealed class ConnectionSettingsChangeEvent(val state: ConnectionState) {}
class ConnectionSettingsStateChange(state: ConnectionState) : ConnectionSettingsChangeEvent(state)

class InvalidConnectionSettings(
    val credentialsProvider: ToolkitCredentialsProvider,
    val region: AwsRegion,
    val cause: Exception,
    state: ConnectionState
) : ConnectionSettingsChangeEvent(state)

class ValidConnectionSettings(val credentialsProvider: ToolkitCredentialsProvider, val region: AwsRegion, state: ConnectionState) :
    ConnectionSettingsChangeEvent(state)
