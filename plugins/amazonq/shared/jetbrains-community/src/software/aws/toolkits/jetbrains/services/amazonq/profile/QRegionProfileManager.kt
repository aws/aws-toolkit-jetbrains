// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonq.profile

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.util.xmlb.annotations.MapAnnotation
import com.intellij.util.xmlb.annotations.Property
import software.amazon.awssdk.core.SdkClient
import software.amazon.awssdk.services.codewhispererruntime.CodeWhispererRuntimeClient
import software.aws.toolkits.core.TokenConnectionSettings
import software.aws.toolkits.core.utils.debug
import software.aws.toolkits.core.utils.getLogger
import software.aws.toolkits.core.utils.tryOrNull
import software.aws.toolkits.core.utils.warn
import software.aws.toolkits.jetbrains.core.AwsClientManager
import software.aws.toolkits.jetbrains.core.awsClient
import software.aws.toolkits.jetbrains.core.credentials.AwsBearerTokenConnection
import software.aws.toolkits.jetbrains.core.credentials.ToolkitConnectionManager
import software.aws.toolkits.jetbrains.core.credentials.pinning.QConnection
import software.aws.toolkits.jetbrains.core.credentials.sono.isSono
import software.aws.toolkits.jetbrains.core.region.AwsRegionProvider
import software.aws.toolkits.jetbrains.utils.notifyInfo
import software.aws.toolkits.resources.AmazonQBundle.message
import software.aws.toolkits.telemetry.MetricResult
import software.aws.toolkits.telemetry.Telemetry
import java.util.Collections
import kotlin.reflect.KClass

@Service(Service.Level.APP)
@State(name = "qProfileStates", storages = [Storage("aws.xml")])
class QRegionProfileManager : PersistentStateComponent<QProfileState>, Disposable {

    // Map to store connectionId to its active profile
    private val connectionIdToActiveProfile = Collections.synchronizedMap<String, QRegionProfile>(mutableMapOf())
    private val connectionIdToProfileList = mutableMapOf<String, Int>()

    // should be call on project startup to validate if profile is still active
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
            val mappedProfiles = QEndpoints.listRegionEndpoints()
                .flatMap { (regionKey, _) ->
                    val awsRegion = AwsRegionProvider.getInstance()[regionKey] ?: return@flatMap emptyList()
                    connection.getConnectionSettings()
                        .withRegion(awsRegion)
                        .awsClient<CodeWhispererRuntimeClient>()
                        .listAvailableProfilesPaginator {}
                        .profiles()
                        .map { p -> QRegionProfile(arn = p.arn(), profileName = p.profileName()) }
                }
            if (mappedProfiles.size == 1) {
                switchProfile(project, mappedProfiles.first(), intent = QProfileSwitchIntent.Update)
            }
            mappedProfiles.takeIf { it.isNotEmpty() }?.also {
                connectionIdToProfileList[connection.id] = it.size
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
                        .profileCount(connectionIdToProfileList[conn.id])
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

        project.messageBus
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
        val profileCounts = connectionIdToProfileList[conn.id] ?: 0
        val activeProfile = connectionIdToActiveProfile[conn.id]
        profileCounts == 0 || (profileCounts > 1 && activeProfile?.arn.isNullOrEmpty())
    } ?: false

    fun shouldDisplayProfileInfo(project: Project): Boolean = getIdcConnectionOrNull(project)?.let { conn ->
        (connectionIdToProfileList[conn.id] ?: 0) > 1
    } ?: false

    fun getQClientSettings(project: Project): TokenConnectionSettings {
        val conn = ToolkitConnectionManager.getInstance(project).activeConnectionForFeature(QConnection.getInstance())
        if (conn !is AwsBearerTokenConnection) {
            error("not a bearer connection")
        }

        val settings = conn.getConnectionSettings()
        val awsRegion = AwsRegionProvider.getInstance()[QEndpoints.Q_DEFAULT_SERVICE_CONFIG.REGION] ?: error("unknown region from Q default service config")

        // TODO: different window should be able to select different profile
        return activeProfile(project)?.let { profile ->
            AwsRegionProvider.getInstance()[profile.region]?.let { region ->
                settings.withRegion(region)
            }
        } ?: settings.withRegion(awsRegion)
    }

    inline fun <reified T : SdkClient> getQClient(project: Project): T = getQClient(project, T::class)

    fun <T : SdkClient> getQClient(project: Project, sdkClass: KClass<T>): T {
        val settings = getQClientSettings(project)
        val client = AwsClientManager.getInstance().getClient(sdkClass, settings)
        return client
    }

    private fun getIdcConnectionOrNull(project: Project): AwsBearerTokenConnection? {
        val connection = ToolkitConnectionManager.getInstance(project).activeConnectionForFeature(QConnection.getInstance())
        if (connection is AwsBearerTokenConnection && !connection.isSono()) {
            return connection
        }
        return null
    }

    companion object {
        private val LOG = getLogger<QRegionProfileManager>()
        fun getInstance(): QRegionProfileManager = service<QRegionProfileManager>()
    }

    override fun dispose() {}

    override fun getState(): QProfileState {
        val state = QProfileState()
        state.connectionIdToActiveProfile.putAll(this.connectionIdToActiveProfile)
        state.connectionIdToProfileList.putAll(this.connectionIdToProfileList)
        return state
    }

    override fun loadState(state: QProfileState) {
        connectionIdToActiveProfile.clear()
        connectionIdToActiveProfile.putAll(state.connectionIdToActiveProfile)

        connectionIdToProfileList.clear()
        connectionIdToProfileList.putAll(state.connectionIdToProfileList)
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
