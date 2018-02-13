package software.aws.toolkits.core.credentials

import org.slf4j.LoggerFactory
import software.amazon.awssdk.auth.profile.Profile
import software.amazon.awssdk.auth.profile.ProfileFile
import software.amazon.awssdk.core.AwsSystemSetting
import software.amazon.awssdk.core.auth.AwsCredentialsProvider
import software.amazon.awssdk.core.auth.ProfileCredentialsProvider
import java.io.PrintWriter
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.ConcurrentHashMap

class ProfileToolkitCredentialsProvider(val name: String, factory: ProfileToolkitCredentialsProviderFactory) : ToolkitCredentialsProvider(factory) {

    override fun toMap(): Map<String, String> = mapOf(
            P_TYPE to factory.type,
            P_PROFILE_NAME to name
    )

    override fun id(): String = factory.type + ":" + name

    override fun displayName(): String = factory.type + ":" + name

    override fun getAwsCredentialsProvider(): AwsCredentialsProvider =
            ProfileCredentialsProvider.builder()
                    .profileFile {
                        it.content((factory as ProfileToolkitCredentialsProviderFactory).profileFilePath)
                                .type(ProfileFile.Type.CREDENTIALS)
                    }
                    .profileName(name)
                    .build()

    companion object {
        const val P_PROFILE_NAME = "profileName"

        @JvmStatic
        fun fromMap(data: Map<String, String>, factory: ProfileToolkitCredentialsProviderFactory): ProfileToolkitCredentialsProvider? {
            val profileType = data[P_TYPE]
            val profileName = data[P_PROFILE_NAME]
            return when {
                profileType != factory.type || profileName == null -> null
                else -> ProfileToolkitCredentialsProvider(profileName, factory)
            }
        }
    }
}

class ProfileToolkitCredentialsProviderFactory(profileFilePath: Path = Paths.get(AwsSystemSetting.AWS_SHARED_CREDENTIALS_FILE.stringValue.get()))
    : ToolkitCredentialsProviderFactory(TYPE, NAME, DESCRIPTION) {

    /**
     * All the AWS profiles in the current configured [profileFilePath], with profile name as the key
     */
    private val profiles = ConcurrentHashMap<String, Profile>()

    /**
     * The profile file path which might not exist. When changing the location, we reload profiles from it.
     */
    var profileFilePath: Path = profileFilePath
        set(value) {
            field = value
            loadFromProfileFile()
        }

    init {
        loadFromProfileFile()
    }

    override fun create(data: Map<String, String>): ToolkitCredentialsProvider? = ProfileToolkitCredentialsProvider.fromMap(data, this)

    fun getByName(name: String): ProfileToolkitCredentialsProvider? =
            tcps.values.firstOrNull { (it as ProfileToolkitCredentialsProvider).name == name } as ProfileToolkitCredentialsProvider

    //TODO To use Austin's existing code for saving profiles
    fun saveToProfileFile() {
        PrintWriter(profileFilePath.toFile()).use {
            profiles.values.forEach { profile ->
                it.println("[${profile.name()}]")
                profile.properties().forEach { property, value ->
                    it.println("${property}=${value}")
                }
            }
        }
    }

    /**
     * Clean out all the current credentials and load all the profiles from the configured [profileFilePath].
     * When the file in [profileFilePath] doesn't exist, do nothing.
     */
    private fun loadFromProfileFile() {
        tcps.clear()
        profiles.clear()

        try {
            ProfileFile.builder()
                    .content(profileFilePath)
                    .type(ProfileFile.Type.CREDENTIALS)
                    .build()
                    .apply {
                        profiles.putAll(this.profiles())
                        profiles.values.forEach { profile ->
                            add(ProfileToolkitCredentialsProvider(profile.name(), this@ProfileToolkitCredentialsProviderFactory))
                        }
                    }
        } catch (e: Exception) {
            LOG.warn("Failed to load AWS profiles from " + profileFilePath, e)
        }
    }

    fun create(profile: Profile): ProfileToolkitCredentialsProvider {
        profiles[profile.name()] = profile.toBuilder().build()
        return add(ProfileToolkitCredentialsProvider(profile.name(), this)) as ProfileToolkitCredentialsProvider
    }

    // Update operation might change the name of the profile. Do a deletion against the original one and add a new one.
    fun update(id: String, profile: Profile) {
        (get(id) as ProfileToolkitCredentialsProvider).apply {
            profiles.remove(this.name)  // Remove the original profile from the profile list
            remove(id)  // Remove the original Toolkit Credentials Provider from the factory
            profiles[profile.name()] = profile.toBuilder().build()   // Update the profile as if creating a new one
            // Add a new Toolkit Credentials Provider
            add(ProfileToolkitCredentialsProvider(profile.name(), this@ProfileToolkitCredentialsProviderFactory))
        }
    }

    override fun remove(id: String): ToolkitCredentialsProvider? =
            super.remove(id)?.apply { profiles.remove((this as ProfileToolkitCredentialsProvider).name) }


    companion object {

        private val LOG = LoggerFactory.getLogger(ProfileToolkitCredentialsProviderFactory::class.java)

        const val TYPE = "profile"
        const val NAME = "AWS Profile"
        const val DESCRIPTION = "AWS Profile based Credential provider"
    }
}