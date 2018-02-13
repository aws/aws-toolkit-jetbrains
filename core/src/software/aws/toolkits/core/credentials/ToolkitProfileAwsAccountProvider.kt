package software.aws.toolkits.core.credentials

import software.amazon.awssdk.auth.profile.Profile
import software.amazon.awssdk.auth.profile.ProfileFile
import software.amazon.awssdk.core.AwsSystemSetting
import software.amazon.awssdk.core.auth.AwsCredentialsProvider
import software.amazon.awssdk.core.auth.ProfileCredentialsProvider
import java.io.PrintWriter
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * Profile file based Toolkit AWS account provider.
 */
class ToolkitProfileAwsAccountProvider(profileFilePath: Path = Paths.get(AwsSystemSetting.AWS_SHARED_CREDENTIALS_FILE.stringValue.get()))
    : ToolkitAwsAccountProvider(ID, NAME, DESCRIPTION) {

    companion object {
        const val ID = "profile"
        const val NAME = "Profile Credentials Provider"
        const val DESCRIPTION = "AWS Credentials Provider provider"
    }

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

    /**
     * Load [ToolkitAwsAccount] from persistent data. If the data is corrupted, ie the underlying profile name is not in the current
     * profile file, don't load it; If not, load it and use its account id.
     */
    override fun justLoadToolkitAwsAccount(account: Map<String, String>): ToolkitAwsAccount? {
        val accountId = account[ToolkitAwsAccount.ACCOUNT_ID]
        val accountName = account[ToolkitAwsAccount.ACCOUNT_NAME]
        val providerId = account[ToolkitAwsAccount.ACCOUNT_PROVIDER_ID]

        return when {
            accountId == null || accountName == null || providerId == null || providerId != id -> null
            !profiles.containsKey(accountName) -> null // The persistent account has been removed from the profile file.
            else -> findAccountIdByName(accountName)!!.let {
                accounts.remove(it)
                ToolkitProfileAwsAccount(accountId, accountName, this@ToolkitProfileAwsAccountProvider)
            }
        }
    }

    /**
     * Clean out all the current credentials and load all the profiles from the configured [profileFilePath].
     * When the file in [profileFilePath] doesn't exist, do nothing.
     */
    private fun loadFromProfileFile() {
        accounts.clear()
        profiles.clear()

        try {
            ProfileFile.builder()
                    .content(profileFilePath)
                    .type(ProfileFile.Type.CREDENTIALS)
                    .build()
        } catch (e: Exception) {null}?.apply {
            profiles.putAll(this.profiles())
            profiles.values.forEach { profile ->
                UUID.randomUUID().toString().apply {
                    accounts[this] = ToolkitProfileAwsAccount(this, profile.name(), this@ToolkitProfileAwsAccountProvider)
                }
            }
        }
    }

    // TODO Super simple implementation of saving. Leave it to Java SDK or implement ourselves?
    // Dump all the credentials to the target file path. Create one if not exists.
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

    fun create(profile: Profile): String {
        profiles[profile.name()] = profile.toBuilder().build()
        return UUID.randomUUID().toString().apply {
            accounts[this] = ToolkitProfileAwsAccount(this, profile.name(), this@ToolkitProfileAwsAccountProvider)
            saveToProfileFile()
        }
    }

    fun update(accountId: String, profile: Profile) {
        accounts[accountId]!!.apply {
            profiles.remove(this.name)  // Remove the original profile from the profile list
            this.name = profile.name()  // This update might change the name of the profile account, forcefully update the profile name
            profiles[this.name] = profile.toBuilder().build()   // Update the profile as if creating a new one
            saveToProfileFile()
        }
    }

    override fun remove(accountId: String) {
        accounts.remove(accountId)?.apply { profiles.remove(this.name) }
        saveToProfileFile()
    }

    /**
     * Find the AWS account ID from the AWS account name or null if not found.
     */
    private fun findAccountIdByName(accountName: String): String? =
            accounts.mapNotNull { if (accountName == it.value.name) it.key else null }.firstOrNull()

    private class ToolkitProfileAwsAccount(id: String, name: String, provider: ToolkitProfileAwsAccountProvider) : ToolkitAwsAccount(id, name, provider) {

        override fun getAwsCredentialsProvider(): AwsCredentialsProvider =
                ProfileCredentialsProvider.builder()
                        .profileFile {
                            it.content((provider as ToolkitProfileAwsAccountProvider).profileFilePath)
                                    .type(ProfileFile.Type.CREDENTIALS)
                        }
                        .profileName(name)
                        .build()
    }
}