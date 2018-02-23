package software.aws.toolkits.core.credentials

import software.amazon.awssdk.auth.profile.Profile
import software.amazon.awssdk.auth.profile.ProfileFile
import software.amazon.awssdk.core.AwsSystemSetting
import software.amazon.awssdk.core.auth.AwsCredentialsProvider
import software.amazon.awssdk.core.auth.ProfileCredentialsProvider
import java.io.PrintWriter
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Credentials file based profiles provider factory.
 */
object ToolkitProfileCredentialsProviderFactory : ToolkitCredentialsProviderFactory {

    override val name: String = "Profile Credentials Provider"
    override val description: String = "AWS Credentials Provider factory"

    private val credentialsProviders = mutableListOf<ToolkitProfileCredentialsProvider>()

    // We find out the default credentials file location. This file could not exist.
    var profileFilePath: Path = Paths.get(AwsSystemSetting.AWS_SHARED_CREDENTIALS_FILE.stringValue.get())
        set(value) {
            field = value
            load()
        }

    private var profileFile: ProfileFile? = null

    private var profiles = mutableMapOf<String, Profile>()

    init {
        load()
    }

    /**
     * The internal ID for this type of Credentials Provider is the same to it's profile name,
     * ie not starting with the reserved prefix '__'. Must [save] to sync in the disk before
     * calling this method.
     */
    override fun getAwsCredentialsProvider(providerId: String): AwsCredentialsProvider? {
        return when {
            providerId.startsWith("__") -> null
            profileFile == null -> null
            !profileFile!!.profiles().containsKey(providerId) -> null
            else -> ProfileCredentialsProvider.builder().profileFile(profileFile).profileName(providerId).build()
        }
    }

    /**
     * Clean out all the current credentials and load all the profiles from the configured [profileFilePath].
     * When the file in [profileFilePath] doesn't exist, do nothing.
     */
    private fun load() {
        credentialsProviders.clear()
        profiles.clear()

        profileFile = try {
            ProfileFile.builder()
                    .content(profileFilePath)
                    .type(ProfileFile.Type.CREDENTIALS)
                    .build()
        } catch (e: Exception) {null}

        if (profileFile != null) {
            profiles.putAll(profileFile!!.profiles())
            profiles.values.forEach { credentialsProviders.add(ToolkitProfileCredentialsProvider(it.name())) }
        }
    }

    // TODO super simple implementation of saving. Leave it to Java SDK or implement ourselves?
    // Dump all the credentials to the target file path. Create one if not exists.
    fun save() {
        PrintWriter(profileFilePath.toFile()).use {
            profiles.values.forEach {profile ->
                it.println("[${profile.name()}]")
                profile.properties().forEach { property, value ->
                    it.println("${property}=${value}")
                }
            }
        }
        load()
    }

    fun create(profileName: String, newProfile: Profile) {
        assert(profileName == newProfile.name())
        modify(profileName, newProfile)
    }

    fun remove(profileName: String) {
        modify(profileName, null)
    }

    fun update(profileName: String, profile: Profile) {
        modify(profileName, profile)
    }

    // Low level API for all kinds of modifications to Profiles
    private fun modify(originalProfileName: String, profile: Profile?) {
        when {
            profile == null -> // Delete this profile if exists
                profiles.remove(originalProfileName)
            originalProfileName != profile.name() -> { // Rename the profile name
                profiles.remove(originalProfileName)
                profiles[profile.name()] = profile
            }
            else -> // Update or create the profile
                profiles[profile.name()] = profile
        }
    }

    override fun listAwsToolkitCredentialsProviders(): List<ToolkitCredentialsProvider> = credentialsProviders

    private class ToolkitProfileCredentialsProvider internal constructor(
            override var profileName: String
    ) : ToolkitCredentialsProvider {

        override val factory: ToolkitCredentialsProviderFactory = ToolkitProfileCredentialsProviderFactory

        override val id: String = profileName
    }
}