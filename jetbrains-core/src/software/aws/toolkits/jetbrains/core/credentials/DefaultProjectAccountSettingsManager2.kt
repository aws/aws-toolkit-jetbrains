package software.aws.toolkits.jetbrains.core.credentials

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.project.Project
import software.aws.toolkits.core.utils.getLogger
import software.aws.toolkits.jetbrains.core.region.AwsRegionProvider

// TODO: I hate this name.... the enum should be ConnectionState...
data class ConnectionState(
    var activeProfile: String? = null,
    var activeRegion: String? = null,
    var recentlyUsedProfiles: List<String> = mutableListOf(),
    var recentlyUsedRegions: List<String> = mutableListOf()
)

@State(name = "accountSettings", storages = [Storage("aws.xml")])
class DefaultProjectAccountSettingsManager2(private val project: Project) : ProjectAccountSettingsManager2(project), PersistentStateComponent<ConnectionState> {
    private val credentialManager = CredentialManager.getInstance()
    private val regionProvider = AwsRegionProvider.getInstance()

    override fun getState(): ConnectionState = ConnectionState(
        activeProfile = connectionSettings.credentials?.id,
        activeRegion = connectionSettings.region?.id,
        recentlyUsedProfiles = recentlyUsedProfiles.elements().map { it.id },
        recentlyUsedRegions = recentlyUsedRegions.elements().map { it.id }
    )

    override fun loadState(state: ConnectionState) {
        // This can be called more than once, so we need to re-do our init sequence
        connectionState = ConnectionValidationState.INITIALIZING

        // TODO: We need to offload this to BG thread in-case the region provider and cred manager arent loaded yet

        state.recentlyUsedRegions.reversed()
            .mapNotNull { regionProvider.regions()[it] }
            .forEach { recentlyUsedRegions.add(it) }

//        state.recentlyUsedProfiles
//            .reversed()
//            .forEach { recentlyUsedProfiles.add(it) }

//        val activeProfile = state.activeProfile ?: ProfileToolkitCredentialsProviderFactory.DEFAULT_PROFILE_DISPLAY_NAME
//        getCredentialProviderOrNull(activeProfile)?.let { provider ->
//            changeCredentialProvider(provider)
//        }
    }

    companion object {
        private val LOGGER = getLogger<DefaultProjectAccountSettingsManager2>()
    }
}
