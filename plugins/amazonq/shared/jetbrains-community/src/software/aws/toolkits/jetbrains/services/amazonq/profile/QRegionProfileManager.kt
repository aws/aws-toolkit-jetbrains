// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonq.profile

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.xmlb.annotations.MapAnnotation
import com.intellij.util.xmlb.annotations.Property
import software.amazon.awssdk.core.SdkClient
import software.aws.toolkits.core.TokenConnectionSettings
import software.aws.toolkits.core.utils.debug
import software.aws.toolkits.core.utils.getLogger
import software.aws.toolkits.core.utils.tryOrNull
import software.aws.toolkits.core.utils.warn
import software.aws.toolkits.jetbrains.core.AwsClientManager
import software.aws.toolkits.jetbrains.core.AwsResourceCache
import software.aws.toolkits.jetbrains.core.credentials.AwsBearerTokenConnection
import software.aws.toolkits.jetbrains.core.credentials.ToolkitConnectionManager
import software.aws.toolkits.jetbrains.core.credentials.pinning.QConnection
import software.aws.toolkits.jetbrains.core.credentials.sono.isSono
import software.aws.toolkits.jetbrains.core.credentials.sso.bearer.BearerTokenAuthState
import software.aws.toolkits.jetbrains.core.credentials.sso.bearer.BearerTokenProviderListener
import software.aws.toolkits.jetbrains.core.region.AwsRegionProvider
import software.aws.toolkits.jetbrains.utils.notifyInfo
import software.aws.toolkits.resources.AmazonQBundle.message
import software.aws.toolkits.telemetry.MetricResult
import software.aws.toolkits.telemetry.Telemetry
import java.time.Duration
import java.util.Collections
import kotlin.reflect.KClass

@Service(Service.Level.APP)
@State(name = "qProfileStates", storages = [Storage("aws.xml")])
class QRegionProfileManager : PersistentStateComponent<QProfileState>, Disposable {

    // Map to store connectionId to its active profile
    private val connectionIdToActiveProfile = Collections.synchronizedMap<String, QRegionProfile>(mutableMapOf())
    private val connectionIdToProfileCount = mutableMapOf<String, Int>()

    init {
        ApplicationManager.getApplication().messageBus.connect(this)
            .subscribe(
                BearerTokenProviderListener.TOPIC,
                object : BearerTokenProviderListener {
                    override fun invalidate(providerId: String) {
                        connectionIdToActiveProfile.remove(providerId)
                        connectionIdToProfileCount.remove(providerId)
                    }
                }
            )
    }

    // should be call on project startup to validate if profile is still active
    @RequiresBackgroundThread
    fun validateProfile(project: Project) {
        val conn = getIdcConnectionOrNull(project)
        val selected = activeProfile(project) ?: return
        val profiles = tryOrNull {
            listRegionProfiles(project)
        }

        if (profiles == null || profiles.none { it.arn == selected.arn }) {
            invalidateProfile(selected.arn)
            switchProfile(project, null, intent = QProfileSwitchIntent.Reload)
            Telemetry.amazonq.profileState.use { span ->
                span.source(QProfileSwitchIntent.Reload.value)
                    .amazonQProfileRegion(selected.region)
                    .ssoRegion(conn?.region)
                    .credentialStartUrl(conn?.startUrl)
                    .result(MetricResult.Failed)
            }
        }
    }

    fun listRegionProfiles(project: Project): List<QRegionProfile>? {
        val connection = getIdcConnectionOrNull(project) ?: return null
        return try {
            val connectionSettings = connection.getConnectionSettings()
            val mappedProfiles = AwsResourceCache.getInstance().getResourceNow(
                resource = QProfileResources.LIST_REGION_PROFILES,
                connectionSettings = connectionSettings,
                timeout = Duration.ofSeconds(30),
                useStale = true,
                forceFetch = false
            )
            if (mappedProfiles.size == 1) {
                switchProfile(project, mappedProfiles.first(), intent = QProfileSwitchIntent.Update)
            }
            mappedProfiles.takeIf { it.isNotEmpty() }?.also {
                connectionIdToProfileCount[connection.id] = it.size
            } ?: error("You don't have access to the resource")
        } catch (e: Exception) {
            LOG.warn(e) { "Failed to list region profiles: ${e.message}" }
            throw e
        }
    }

    fun activeProfile(project: Project): QRegionProfile? = getIdcConnectionOrNull(project)?.let { connectionIdToActiveProfile[it.id] }

    fun hasValidConnectionButNoActiveProfile(project: Project): Boolean = getIdcConnectionOrNull(project) != null && activeProfile(project) == null

    fun switchProfile(project: Project, newProfile: QRegionProfile?, intent: QProfileSwitchIntent) {
        val conn = getIdcConnectionOrNull(project) ?: return

        val oldProfile = connectionIdToActiveProfile[conn.id]
        if (oldProfile == newProfile) return

        connectionIdToActiveProfile[conn.id] = newProfile
        LOG.debug { "Switch from profile $oldProfile to $newProfile for project ${project.name}" }

        if (newProfile != null) {
            if (intent == QProfileSwitchIntent.User || intent == QProfileSwitchIntent.Auth) {
                notifyInfo(
                    title = message("action.q.profile.usage.text"),
                    content = message("action.q.profile.usage", newProfile.profileName),
                    project = project
                )

                Telemetry.amazonq.didSelectProfile.use { span ->
                    span.source(intent.value)
                        .amazonQProfileRegion(newProfile.region)
                        .profileCount(connectionIdToProfileCount[conn.id])
                        .ssoRegion(conn.region)
                        .credentialStartUrl(conn.startUrl)
                        .result(MetricResult.Succeeded)
                }
            } else {
                Telemetry.amazonq.profileState.use { span ->
                    span.source(intent.value)
                        .amazonQProfileRegion(newProfile.region)
                        .ssoRegion(conn.region)
                        .credentialStartUrl(conn.startUrl)
                        .result(MetricResult.Succeeded)
                }
            }
        }

        ApplicationManager.getApplication().messageBus
            .syncPublisher(QRegionProfileSelectedListener.TOPIC)
            .onProfileSelected(project, newProfile)
    }

    private fun invalidateProfile(arn: String) {
        val updated = connectionIdToActiveProfile.filterValues { it.arn != arn }
        connectionIdToActiveProfile.clear()
        connectionIdToActiveProfile.putAll(updated)
    }

    // for each idc connection, user should have a profile, otherwise should show the profile selection error page
    fun isPendingProfileSelection(project: Project): Boolean = getIdcConnectionOrNull(project)?.let { conn ->
        val profileCounts = connectionIdToProfileCount[conn.id] ?: 0
        val activeProfile = connectionIdToActiveProfile[conn.id]
        profileCounts == 0 || (profileCounts > 1 && activeProfile?.arn.isNullOrEmpty())
    } ?: false

    fun shouldDisplayProfileInfo(project: Project): Boolean = getIdcConnectionOrNull(project)?.let { conn ->
        (connectionIdToProfileCount[conn.id] ?: 0) > 1
    } ?: false

    fun getQClientSettings(project: Project, profile: QRegionProfile?): TokenConnectionSettings {
        val conn = ToolkitConnectionManager.getInstance(project).activeConnectionForFeature(QConnection.getInstance())
        if (conn !is AwsBearerTokenConnection) {
            error("not a bearer connection")
        }

        val settings = conn.getConnectionSettings()
        val defaultRegion = AwsRegionProvider.getInstance()[QEndpoints.Q_DEFAULT_SERVICE_CONFIG.REGION] ?: error("unknown region from Q default service config")

        val regionId = profile?.region ?: activeProfile(project)?.region
        val awsRegion = regionId?.let { AwsRegionProvider.getInstance()[it] } ?: defaultRegion

        return settings.withRegion(awsRegion)
    }

    inline fun <reified T : SdkClient> getQClient(project: Project): T = getQClient(project, null, T::class)
    inline fun <reified T : SdkClient> getQClient(project: Project, profile: QRegionProfile): T = getQClient(project, profile, T::class)

    fun <T : SdkClient> getQClient(project: Project, profile: QRegionProfile?, sdkClass: KClass<T>): T {
        val settings = getQClientSettings(project, profile)
        val client = AwsClientManager.getInstance().getClient(sdkClass, settings)
        return client
    }

    fun getIdcConnectionOrNull(project: Project): AwsBearerTokenConnection? {
        val manager = ToolkitConnectionManager.getInstance(project)
        val connection = manager.activeConnectionForFeature(QConnection.getInstance()) as? AwsBearerTokenConnection
        val state = manager.connectionStateForFeature(QConnection.getInstance())

        return if (connection != null && !connection.isSono() && state == BearerTokenAuthState.AUTHORIZED) {
            connection
        } else {
            null
        }
    }

    companion object {
        private val LOG = getLogger<QRegionProfileManager>()
        fun getInstance(): QRegionProfileManager = service<QRegionProfileManager>()
    }

    override fun dispose() {}

    override fun getState(): QProfileState {
        val state = QProfileState()
        state.connectionIdToActiveProfile.putAll(this.connectionIdToActiveProfile)
        state.connectionIdToProfileList.putAll(this.connectionIdToProfileCount)
        return state
    }

    override fun loadState(state: QProfileState) {
        connectionIdToActiveProfile.clear()
        connectionIdToActiveProfile.putAll(state.connectionIdToActiveProfile)

        connectionIdToProfileCount.clear()
        connectionIdToProfileCount.putAll(state.connectionIdToProfileList)
    }
}

class QProfileState : BaseState() {
    @get:Property
    @get:MapAnnotation
    val connectionIdToActiveProfile by map<String, QRegionProfile>()

    @get:Property
    @get:MapAnnotation
    val connectionIdToProfileList by map<String, Int>()
}
