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
import com.intellij.util.xmlb.annotations.MapAnnotation
import com.intellij.util.xmlb.annotations.Property
import org.jetbrains.annotations.VisibleForTesting
import software.amazon.awssdk.core.SdkClient
import software.amazon.awssdk.services.codewhispererruntime.CodeWhispererRuntimeClient
import software.aws.toolkits.core.TokenConnectionSettings
import software.aws.toolkits.core.utils.debug
import software.aws.toolkits.core.utils.getLogger
import software.aws.toolkits.jetbrains.core.AwsClientManager
import software.aws.toolkits.jetbrains.core.awsClient
import software.aws.toolkits.jetbrains.core.credentials.AwsBearerTokenConnection
import software.aws.toolkits.jetbrains.core.credentials.ToolkitConnectionManager
import software.aws.toolkits.jetbrains.core.credentials.pinning.QConnection
import software.aws.toolkits.jetbrains.core.credentials.sono.isSono
import software.aws.toolkits.jetbrains.core.region.AwsRegionProvider
import software.aws.toolkits.jetbrains.utils.notifyInfo
import software.aws.toolkits.resources.AmazonQBundle.message
import java.util.Collections
import kotlin.reflect.KClass

@Service(Service.Level.APP)
@State(name = "qProfileStates", storages = [Storage("aws.xml")])
class QRegionProfileManager : PersistentStateComponent<QProfileState>, Disposable {

    // Map to store connectionId to its active profile
    private val connectionIdToActiveProfile = Collections.synchronizedMap<String, QRegionProfile>(mutableMapOf())
    private val connectionIdToProfileList = mutableMapOf<String, MutableList<QRegionProfile>>()

    fun listRegionProfiles(project: Project): List<QRegionProfile>? {
        val connection = ToolkitConnectionManager.getInstance(project).activeConnectionForFeature(QConnection.getInstance())
        if (connection !is AwsBearerTokenConnection || connection.isSono()) {
            return null
        }
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

        return mappedProfiles.takeIf { it.isNotEmpty() }?.also {
            connectionIdToProfileList[connection.id] = it.toMutableList()
        } ?: error("no available profiles")
    }

    fun activeProfile(project: Project): QRegionProfile? {
        val conn = ToolkitConnectionManager.getInstance(project).activeConnectionForFeature(QConnection.getInstance())
        return if (conn !is AwsBearerTokenConnection || conn.isSono()) {
            null
        } else {
            connectionIdToActiveProfile[conn.id]
        }
    }

    @VisibleForTesting
    fun switchProfile(project: Project, newProfile: QRegionProfile?) {
        val conn = ToolkitConnectionManager.getInstance(project)
            .activeConnectionForFeature(QConnection.getInstance()) as? AwsBearerTokenConnection
            ?: return

        if (conn.isSono() || newProfile == null || newProfile.arn.isEmpty()) return

        val oldProfile = connectionIdToActiveProfile[conn.id]
        if (oldProfile == newProfile) return

        connectionIdToActiveProfile[conn.id] = newProfile
        LOG.debug { "Switch from profile $oldProfile to $newProfile for project ${project.name}" }

        ApplicationManager.getApplication().messageBus
            .syncPublisher(QRegionProfileSelectedListener.TOPIC)
            .onProfileSelected(project, newProfile)

        notifyInfo(
            title = message("action.q.switchProfiles.dialog.panel.text"),
            content = message("action.q.profile.usage", newProfile.profileName),
            project = project
        )
    }

    fun shouldProfileSelection(project: Project): Boolean = shouldDisplayCustomNode(project) && activeProfile(project)?.arn.isNullOrEmpty()

    fun shouldDisplayCustomNode(project: Project): Boolean =
        (
            ToolkitConnectionManager.getInstance(project)
                .activeConnectionForFeature(QConnection.getInstance()) as? AwsBearerTokenConnection
            )?.takeIf { !it.isSono() }
            ?.let { (connectionIdToProfileList[it.id]?.size ?: 0) > 1 } ?: false

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
    val connectionIdToProfileList by map<String, MutableList<QRegionProfile>>()
}
